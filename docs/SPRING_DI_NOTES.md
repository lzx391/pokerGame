# Spring Dependency Injection：`@Autowired` vs constructor injection（cheat sheet）

面向日常复习：构造器注入、`@Autowired`、与若依常见写法的对照，并结合本仓库 WebSocket 相关类举例。

---

## 1. 核心结论（先记这一句）

**字段 `@Autowired` 和构造器注入，本质都是「Spring 把依赖装进 Bean」**，只是依赖落在「字段」还是「构造器参数」上。

---

## 2. 若依里常见的：字段注入

典型形态：**没有手写构造器**，依赖写在字段上：

```java
@Service
public class XxxServiceImpl implements IXxxService {

    @Autowired
    private XxxMapper xxxMapper;
}
```

Spring 大致会：先用**无参构造**创建对象，再通过反射把 `Mapper` 赋给字段。所以**没有构造器很正常**，不代表没做注入。

---

## 3. 构造器注入（本仓库常见）

典型形态：**`private final` 依赖 + 唯一构造器**，构造器里 `this.xxx = xxx`：

```java
@Component
public class DpGameRoomWebSocketHandler extends TextWebSocketHandler {

    private final DpGameRoomPushService pushService;

    public DpGameRoomWebSocketHandler(DpGameRoomPushService pushService) {
        this.pushService = pushService;
    }
}
```

含义：

- 构造器参数：由调用方把「真正的 `PushService` 实例」传进来（在应用里通常是 **Spring 容器**传）。
- `this.pushService = pushService`：保存到当前对象，后面业务方法用的就是容器里的那个 Bean。
- `final`：引用一旦在构造器里赋好，不能再换，适合「必选依赖」。

---

## 4. 为什么唯一构造器常常不写 `@Autowired`？

从 **Spring 4.3** 起：若类只有**一个**构造器，Spring 会**自动**按参数类型从容器里找 Bean 填进去，效果类似在构造器上写了 `@Autowired`。

**多个构造器**时，要在「要选的那个」上显式写 `@Autowired`，否则 Spring 不知道用哪个。

---

## 5. `@Component` / `@Service` 会不会都这样？

会。**`@Component`、`@Service`、`@Repository`、`@Controller`** 在「能不能被注入、怎么注入」上是一类东西，只是语义分层不同（服务层、控制层等）。  
**不是**「用了 `@Service` 就必须写构造器」——若依用字段注入也一样是 `@Service`。

---

## 6. 谁负责 `new`？和「单例」

业务代码里一般**不要**自己 `new` 这些 Bean，而是交给 **Spring 容器**创建。

创建顺序（简化理解，与本项目一致）：

1. 先创建 `DpGameRoomPushService`（及它自己的依赖）。
2. 再创建 `DpGameRoomWebSocketHandler`，调用构造器时传入上面的 `PushService`。
3. 再创建 `WebSocketGameRoomConfig`，注入**已经装配好**的 `DpGameRoomWebSocketHandler`。

默认作用域下，这些往往是 **单例**：全应用通常**一份**实例，不是「每个调用者 new 一个」。

`WebSocketGameRoomConfig` 只是**被注入**已经就绪的 Handler，通常**不是**它自己去 `new Handler(pushService)`。

---

## 7. 构造器注入的常见优点（复习用）

| 点 | 说明 |
|----|------|
| 依赖显式 | 看构造器参数就知道「必须有哪些依赖」 |
| 可 `final` | 引用不可变，更符合「必选依赖」 |
| 单测友好 | 可 `new Handler(mockPushService)`，不启 Spring |

字段注入也能用，若依大量采用，**两种都是合法用法**，属于项目风格选择。

---

## 8. 本项目对应文件（对照看代码）

| 文件 | 作用 |
|------|------|
| `src/main/java/com/example/mgdemoplus/websocket/DpGameRoomWebSocketHandler.java` | `@Component` + 构造器注入 `DpGameRoomPushService` |
| `src/main/java/com/example/mgdemoplus/config/WebSocketGameRoomConfig.java` | `@Configuration` + 构造器注入 `DpGameRoomWebSocketHandler`，用于注册 WebSocket 路径 |
| `docs/WEBSOCKET.md` | WebSocket 协议与行为说明（与 DI 笔记互补） |

---

## 9. 一句话对照若依习惯

若依：**字段 `@Autowired`，常无手写构造器。**  
本仓库 WebSocket 相关：**构造器注入，唯一构造器时可不写 `@Autowired`。**  
**都是依赖注入，只是落点不同。**
