# JSON、`Map`、`Result`：序列化与服务端／前端对齐备忘

本文档整理后端与前端协作时常见的几个概念，便于日后查阅。与项目里 `Result`、`Map<String, Object>`、以及牌谱 `payload_json` 等用法对应。

---

## 1. 接口里常见的「统一外壳」

很多 HTTP 接口会用一层固定结构包住业务数据，例如：

| 字段 | 含义（常见约定） |
|------|------------------|
| `success` | 是否成功 |
| `code` | 业务码 / 错误码 |
| `message` | 给人看的提示文案 |
| `data` | 真正的业务数据 |

业务数据有时用 **`Map<String, Object>`** 表示：灵活，一个接口里可以塞不同形状的内容；代价是类型不如固定实体类清晰。

---

## 2. `Map<String, Object>` 怎么理解

- **`Map`**：键值对集合（像字典）：「名字 → 内容」。
- **`String`**：**键**的类型。对应 JSON 里对象的**属性名**，例如 `"players"`、`"actions"`。
- **`Object`**：**值**的类型。因为一段 JSON 里可能有数字、字符串、数组、嵌套对象，Java 用 `Object` 表示「可能是其中任意一种」。

**和前端的关系：** 浏览器收到的永远是 **JSON 文本**；解析成 JavaScript 对象后，用 `res.players`、`res.data` 访问，是因为 JSON 里本来就有名为 `players`、`data` 的字段——**本质是 JSON 的结构**，不是「前端能直接看见 Java 的 Map 类型」。

---

## 3. JSON 与「字符串当键」

JSON 对象示例：

```json
{
  "roomId": "d181f488",
  "players": [
    { "nickname": "MSI", "chips": 500 }
  ]
}
```

- 这里的 `"roomId"`、`"players"` 就是**字符串形式的键**。
- 在 Java 里若用 `Map<String, Object>` 表示顶层，则 `get("roomId")`、`get("players")` 与上述字段一一对应。
- 若用带 `getRoomId()` / `getPlayers()` 的 **实体类（POJO）**，Jackson 等库序列化后，JSON 里通常仍是 `"roomId"`、`"players"`（取决于命名策略），前端写法可以相同。

**结论：** 前端 `res.players` 来自 JSON 属性名；后端无论是 **Map** 还是 **POJO**，只要序列化出的 JSON 一致，前端用法就一致。

---

## 4. 序列化 vs 反序列化

| 方向 | 名称 | 含义（本项目常见：JSON） |
|------|------|---------------------------|
| 内存结构 → 字符串 | **序列化** | 对象、`Map` 等 → **JSON 字符串**（例如写入数据库文本字段、或返回给前端的响应体） |
| 字符串 → 内存结构 | **反序列化** | **JSON 字符串** → 对象、`Map` 等（例如从数据库读出 `payload_json` 再解析） |

**记忆：** 「序列」成一串字就是序列化；从一串字「还原」成结构就是反序列化。

---

## 5. 与本项目相关的例子：牌谱 `payload_json`

- **写入数据库（序列化）：** 对局结束时，把一局内的座位、公共牌、行动等组装成对象，再转成 JSON 字符串，存入 `DpObservedHandHistory.payloadJson` 一类字段。
- **读取详情（反序列化）：** 从数据库取出字符串，用 `ObjectMapper.readValue(json, new TypeReference<Map<String, Object>>() {})` 转成 `Map<String, Object>`，再放入详情 DTO 返回给前端。

因此：**提库时「字符串 → Map」这一步，就是 JSON 反序列化。**

---

## 6. Map 与实体类（POJO）怎么选（简要）

| 方式 | 优点 | 缺点 |
|------|------|------|
| `Map<String, Object>` | 灵活，字段多变时改起来快 | 编译期不知道有哪些 key，易拼错 |
| 固定 DTO / 实体类 | 类型清晰，IDE 可提示、重构安全 | 结构每变一次可能要改类 |

存库的 JSON 若结构稳定，也可以反序列化成**专门的 Java 类**，而不是 `Map`；`Map` 更适合「先通用接住，再按需取值」的路径。

---

## 7. 本仓库可参考的代码位置（按需打开）

- 统一返回：`com.vo.Result`（若项目中有使用）。
- 牌谱详情中的 payload：`DpHandHistoryServiceImpl#getDetail`（字符串 → `Map`）。
- 牌谱写入时的 payload 结构：`DpHandHistoryPersistServiceImpl` 内部 `Payload` 与 `actions`、`seatsAtStart` 等字段。
- 实体字段：`DpObservedHandHistory#payloadJson`。

---

## 8. 一句话总览

- **JSON** 是前后端、以及「数据库里存一整段结构化数据」时常用的**文本格式**。
- **`Map<String, Object>`** 是 Java 里一种**通用**的内存表示：键对应 JSON 属性名，值对应各种类型的内容。
- **序列化** = 结构变字符串；**反序列化** = 字符串变结构；读库里的 JSON 字段再转成 `Map` 属于**反序列化**。

---

*若你发现项目约定与本文不一致，以实际接口与实体定义为准，并可直接改本文档保持同步。*
---
# 自己的笔记  
## 返回的数据结构分两种  
一个是直接返回特定实体类，如 public DpRoom createRoom这种的  
一个是不返回特定的实体类，比如返回DpRoom,DpUser等等...只是一种通用格式的对象结构就用 Map<String, Object>,String对应的就是  "roomId": "d181f488",里的roomId，Object对应的就是任意的数据结构如d181f488