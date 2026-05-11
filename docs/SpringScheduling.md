# Spring 定时任务：`@Scheduled` vs `Timer`、大厅对齐与本项目配置项

面向在本项目中**加定时逻辑**、或理解**大厅对齐调度器**与**房间内核 `Timer`** 的差异。默认读者已会用 Spring Boot 启动应用。

---

## 1. Spring 定时在干什么？

开启 **`@EnableScheduling`** 后，Spring 会扫描带 **`@Scheduled`** 的方法，按你配置的规则**周期性调用**这些方法（由内部的 `TaskScheduler` 执行）。

典型形态：

- 某个类标 **`@Component` / `@Service`**（交给容器管理）。
- 某个**无参或固定签名**的方法上标 **`@Scheduled`**，并指定间隔或 cron。

**和「写个 `while(true) { sleep; }`」的区别**：线程与触发由框架统一管理，间隔可配，也便于以后换线程池或做监控。

---

## 2. 本仓库里的两个例子

### 2.1 Spring：`DpRoomLobbyReconcileScheduler`

- 路径：`src/main/java/com/example/mgdemoplus/scheduler/DpRoomLobbyReconcileScheduler.java`
- 作用：把 MySQL 表 **`dp_room_lobby`** 里仍展示的大厅行，与当前进程内存 **`roomMap`** 对齐；清掉**服务重启等**场景下的「幽灵房」。
- **`ApplicationRunner`**：应用启动完成后**立刻执行一次** `run()`，不等第一次 `@Scheduled` 周期。
- **`@Scheduled`**：默认**上一次执行结束**后再隔固定毫秒执行下一次（`fixedDelay`），避免任务叠在一起。

配置（`application.properties`）：

| 配置项 | 含义 |
|--------|------|
| `mgdemoplus.dp-lobby-reconcile-enabled` | 是否启用对齐任务；默认 `true`。**多实例**且每台各有自己的 `roomMap` 时，应设为 `false` 或改为分布式方案，否则会误删别机上的大厅行。 |
| `mgdemoplus.dp-lobby-reconcile-ms` | 周期间隔（毫秒），默认 `60000`。 |

启用调度的入口：`MgDemoPlusApplication` 上的 **`@EnableScheduling`**。

### 2.2 JDK：`DpRoomServiceImpl` 里的 `java.util.Timer`

- 在 `DpRoomServiceImpl` 构造方法里 **`new Timer().scheduleAtFixedRate(..., 0, 1000)`**。
- 作用：大约**每秒**扫所有内存房间：心跳超时踢人、NPC 行动、准备超时、空房 `removeRoom` 等，与 **`roomMap` 强耦合**。

这是**手写定时器**，不经过 Spring Scheduling，配置写死在代码里（`1000` ms）。

---

## 3. 两种方式怎么选（对照表）

| 维度 | Spring `@Scheduled` | `java.util.Timer` |
|------|---------------------|-------------------|
| 生命周期 | 随应用启停，与 Bean 一致 | 自己 `new`，需留意是否 daemon、异常对后续 tick 的影响 |
| 配置 | 可用 `${property:default}` 外置到配置文件 | 多在代码里写死间隔 |
| 依赖其它 Bean | 构造器/`@Autowired` 注入即可 | 若在 Service 内部 new，天然能访问该实例字段 |
| 适用场景 | 对齐、清理、统计、与业务弱耦合的周期任务 | 与单个对象生命周期强绑定、历史遗留或极简单的循环 |
| 多线程 | 可配置 `TaskScheduler` / 线程池 | `Timer` 单线程执行，长任务会阻塞后续 |

**建议**：新项目里**优先**用 **`@Scheduled` + 独立类**；仅在逻辑必须和某一个对象构造周期完全绑死时，再考虑 `Timer` 或 `ScheduledExecutorService`。

---

## 4. 在本项目里新增一条 Spring 定时任务：步骤清单

1. **确认已开启调度**  
   本仓库已在 `MgDemoPlusApplication` 使用 `@EnableScheduling`。若拆模块或新启动类，勿漏。

2. **新建 `@Component`（推荐独立类）**  
   把定时逻辑放在单独类里，职责清晰，便于单测和开关。

3. **注入依赖**  
   使用构造器注入需要调用的 `Service` / `Mapper`（与 `DpRoomLobbyReconcileScheduler` 相同风格）。

4. **在方法上使用 `@Scheduled`**  
   - **`fixedDelay` / `fixedDelayString`**：上次**执行结束**后间隔再执行（本仓库大厅对齐用法，适合可能略慢的任务）。  
   - **`fixedRate` / `fixedRateString`**：按固定频率**开始**新一次执行；若单次执行超过周期，可能重叠，需自行防重入或缩短任务。  
   - **`cron`**：按表达式触发（如每天固定时刻）。  
   - **`initialDelay`**：首次延迟，可与上面组合。

5. **需要「启动立刻跑一次」**  
   可实现 **`ApplicationRunner`** 或 **`CommandLineRunner`**，在 `run` 里调用与定时任务相同的私有方法（与当前 `DpRoomLobbyReconcileScheduler` 一致）。

6. **可配置化**  
   间隔、开关写入 `application.properties`，便于环境差异与运维调整。

7. **多实例部署**  
   默认每台 JVM 都会跑同一套 `@Scheduled`。若任务必须是**全局单跑**（例如全库只扫一次），需要分布式锁、ShedLock、Quartz 集群等，再单独设计。

8. **（可选）自定义调度线程池**  
   任务多或耗时长时，配置 `TaskScheduler` Bean，避免默认单线程队列堆积。

---

## 5. 参考类与配置一览

| 用途 | 类或配置 |
|------|-----------|
| 开启调度 | `MgDemoPlusApplication` → `@EnableScheduling` |
| 大厅表与内存对齐 | `DpRoomLobbyReconcileScheduler` |
| 对齐业务实现 | `DpRoomHallService#reconcileLobbyWithRuntimeRoomIds` |
| 内存房间 ID 集合 | `DpRoomServiceImpl#getRoomIdsInMemory` |
| 房间内核每秒循环 | `DpRoomServiceImpl` 构造方法内 `Timer` |
| 开关与间隔 | `mgdemoplus.dp-lobby-reconcile-enabled`、`mgdemoplus.dp-lobby-reconcile-ms` |

---

## 6. 延伸阅读

- Spring Boot 官方文档：[Task Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)（调度与 `@EnableScheduling` 行为以当前 Spring 版本为准）。  
- 本仓库依赖注入风格见 **[SPRING_DI_NOTES.md](SPRING_DI_NOTES.md)**、**[Spring依赖注入复习笔记.md](Spring依赖注入复习笔记.md)**。
