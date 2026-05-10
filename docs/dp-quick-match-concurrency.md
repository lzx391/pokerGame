# 快速匹配里的「并发锁」：零基础说明

本篇只讲：**大厅快速匹配**（`DpRoomServiceImpl#quickMatchJoinAndReady`、`POST /dpRoom/quickMatch2`）里，为什么要对**单个房间**加锁、`synchronized` 在干什么、和第一个人办完第二个人会怎么样。不要求你学过操作系统或 JVM internals。

---

## 1. 我们要解决的真实问题

多个人几乎同时点「快速匹配」，后台可能都算出来：**同一间房还剩一个名额**。如果两个人**同时往里写**观众席 / `waitNextHand`，就容易出现：

- 两人读到的都是「还能加」，但写完以后**超过上限**；
- 或者一人已经 `joinRoom` 成功，另一人的 `readyNextHand` 再失败，表里状态很乱。

所以需要一个约定：**同一间房间**里，和「名额、进房、候补下一局」相关的那小段逻辑，在同一时刻只允许**一条请求**完整执行完；下一条再进来时，看到的是**已经更新过后**的人数。

这就叫**避免并发写同一份数据的竞态**（race）。我们用的工具是 Java 自带的 **`synchronized (房间对象)`**。

---

## 2. `synchronized (r)` 是什么（直观版）

在代码里，`r` 是内存里这一间房的 **`DpRoomBO` 实例**（一户一张桌子一个对象）。

```text
synchronized (r) {
    // A：再检查名额
    // B：joinRoom
    // C：readyNextHand（已开局）或 ensureLobbyReady（未开局）
}
```

含义可以理解为：

1. 谁先进这个块，谁先**霸占**和这个 `r` 绑在一起的**一把锁**。
2. 别人如果也想 `synchronized (同一个 r)`，会**在门口等**，直到里面的代码整段执行完，锁才被释放。
3. 这和「两个人不能同时进同一个厕所隔间」是同一类思路：同一时间只有一个人能办完一整套操作。

释放锁以后，排在后面的线程**拿不到旧快照**，而是从**最新的房间状态**再跑一遍那段逻辑（再一次检查名额、`join`、`ready`）。

---

## 3. 你问的：是不是「前一个弄完再放人，下一个再查能不能加」？

**是的。**

更准确一点：

| 顺序 | 发生的事情 |
|------|------------|
| 线程甲 | 拿到 `房间X` 的锁 → 进房、候补 OK → 出块，释放锁 |
| 线程乙（也在抢房间 X） | 在 `synchronized(房间X)` 外等 → 锁释放后进入 → **用甲改完之后的名单**再算名额、再尝试加入 |

所以如果「最后一席」被甲占了，乙拿到锁之后会看到已满，`readyNextHand` 失败；我们还会在「乙是这一波才新进房」时 `exitRoom` 回滚，避免半吊子状态。

**注意：** 线程乙如果只排队等房间 X，没机会试别的房间。实际实现里是：对**某一个候选房间**加锁失败后，可以继续 `continue` 去试**排序列表里的下一个房间**——那是业务循环，不是「永远卡在一个房间队列里」的意思。

---

## 4. 不同房间两把锁会不会互相卡住？

不会。

- 抢 **房间 X** → `synchronized(房间 X 的对象)`  
- 抢 **房间 Y** → `synchronized(房间 Y 的对象)`  

这是**两把不同的锁**，两个人可以各自进各自的块，并行执行。并发度还在，只是「**同一间房**不要乱」。

---

## 5. 和项目代码的对应关系（方便你对照源码）

入口：

- **`DpRoomServiceImpl.quickMatchJoinAndReady`**
  - 先处理「昵称已经在某一房里」的旧情况（对已存在的房间也会被 `synchronized`）；
  - 再对每个**候选房间** `for (DpRoomBO r : candidates)` 里包一层 **`synchronized (r) { … }`**，里面做：**二次校验空位、`joinRoom`、然后 `readyNextHand`（局中）或 `ensureLobbyReady`（未开局）**。

控制器：

- **`POST /dpRoom/quickMatch2`**：校验 JWT 昵称后调用上面的方法。

你可以在 IDE 里直接搜 `quickMatchJoinAndReady` 或 `synchronized (r)` 跳到那一段。

---

## 6. 局限：为什么这么写「单机够用」？

`synchronized (r)` **只在当前这台 JVM / 这一个 Java 进程里**有效：`roomMap` 里的 `DpRoomBO` 是一份内存里的真相。

如果有一天 **多台机器跑同一套后端**，每台机器有自己一份 `roomMap`，那两个人可能落到**两台不同机器**，就**拿不到同一把 synchronized 锁**——那就要用 **Redis 分布式锁、或把匹配逻辑集中到一个实例、或改用共享存储原子扣减席位**等方案。那是架构升级话题，和本篇「先理解锁在单进程里干什么」是两层问题。

---

## 7. 额外一把「快匹分配锁」（`dpQuickMatchAssignmentLock`）

快匹除对每个候选房 `synchronized (r)` 外，还把 **`quickMatchJoinAndReady` 的主流程**（含扫 `roomMap`、尝试进房）与 **`tryDrainDefaultQuickMatchPairs` 里「从默认队列摘下两人 → `createRoom` / `joinRoom` / `startGame`」** 串在同一把 `dpQuickMatchAssignmentLock` 上。这样不会出现：玩家 A 已被队列逻辑「摘出」但新房尚未建好时，另一条请求又让 A 进了别的公开桌，却仍按旧配对再为 A 建一桌的错乱窗口。

服务端 **`joinRoom` / `readyNextHand` / `exitRoom`** 等对名单与席位的写入也在 **`synchronized (同一 DpRoomBO)`** 内完成（与快匹里包在 `synchronized(r)` 的块可重入叠加），避免大厅「加入房间」与快匹并发写同一 `players` 列表。

## 8. 小结（背三句就够）

1. **同一张桌子（同一个 `DpRoomBO`）**：同一时刻只允许一个请求在「进房 + 候补」这段里改它，所以用 `synchronized (r)`（以及相关 API 内部的同对象锁）。  
2. **下一个人**：等锁没了再进来，**重新看名额**，可能已经加不进——这是预期行为。  
3. **不同桌子**：Different `r` → **不同锁**，可以并行；**默认 FIFO 配对**还与 `dpQuickMatchAssignmentLock` 配合，收窄跨请求的时间窗。

若你想继续往深走，推荐阅读顺序：**本篇 → Java 入门书里的「线程与 `synchronized` / 监视器（intrinsic lock）」一章**；和本项目强相关时再对照 **`DpRoomServiceImpl`** 里的 `quickMatchJoinAndReady` 读一遍。

---

*文档面向学习与维护说明；接口与业务流程仍以 `DPGAME.md`（若允许改动）与实际代码为准。*
