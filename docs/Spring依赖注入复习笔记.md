# Spring 依赖注入复习笔记

面向：已接触过若依（字段 `@Autowired`）与本项目（构造器注入）时的对照理解。

---

## 1. 核心结论（先记这一句）

**字段 `@Autowired` 和构造器注入，作用一样：都是 Spring 把依赖装进 Bean。**  
差别主要在：**依赖写在字段上，还是写在构造器参数上**；以及能否用 `final`、测试是否好写。

---

## 2. 若依里常见的写法（字段注入）

```java
@Service
public class XxxServiceImpl implements IXxxService {

    @Autowired
    private XxxMapper xxxMapper;
}
```

- Spring 先用**无参构造**创建对象（你没写构造器时，Java 会提供默认无参构造器）。
- 再通过反射把容器里的 `Mapper` **塞进字段**。
- 所以**很多类没有显式构造器**，照样能注入。

---

## 3. 本项目里常见的写法（构造器注入）

示例：`DpGameRoomWebSocketHandler` 接收 `DpGameRoomPushService`。

```java
@Component
public class DpGameRoomWebSocketHandler extends TextWebSocketHandler {

    private final DpGameRoomPushService pushService;

    public DpGameRoomWebSocketHandler(DpGameRoomPushService pushService) {
        this.pushService = pushService;
    }
}
```

- 构造器参数：由 Spring 在创建该 Bean 时，按**类型**从容器里找 Bean 传进来。
- `this.pushService = pushService`：把传进来的实例保存到字段（`final` 必须在构造器里赋值一次）。
- **从 Spring 4.3 / Boot 2 起**：若类只有**一个**构造器，构造器上**可以不写** `@Autowired`，效果等价于“Spring 自动给这个构造器做装配”。

---

## 4. `@Component` 和 `@Service` 会不一样吗？

**注入机制一样。**  
`@Service`、`@Repository`、`@Controller` 都是基于 `@Component` 的**语义化别名**（标明层次），对“怎么注入依赖”没有本质区别。

---

## 5. 谁在用构造器？是业务代码吗？

通常**不是**你在 Controller 里 `new Handler(...)`。

创建顺序由 **Spring 容器**按依赖关系决定，例如：

1. 创建 `DpGameRoomPushService`（及其依赖）。
2. 调用 `new DpGameRoomWebSocketHandler(pushService)`。
3. 创建 `WebSocketGameRoomConfig` 时，把**已经装配好**的 `dpGameRoomWebSocketHandler` 注入进去。

因此：`WebSocketGameRoomConfig` 拿到的 Handler **里面已经带好** `pushService`；配置类不负责“帮 Handler 塞 pushService”，那是上一步容器创建 Handler 时完成的。

构造器还方便**单元测试**：测试里可以 `new DpGameRoomWebSocketHandler(mockPushService)`，不启动 Spring。

---

## 6. “new 一个给调用者”要注意的一点

默认作用域下，很多 Bean 是**单例**：整个应用通常**共用一个** `DpGameRoomWebSocketHandler` 实例，并不是每个请求都 new 一个。  
“注入”指的是容器把这个**已创建、已装配**的实例交给需要它的另一个 Bean。

---

## 7. 和若依对比速查表

| 项目       | 若依常见                     | 构造器注入常见                         |
|------------|------------------------------|----------------------------------------|
| 构造器     | 常省略                       | 显式写出，参数即依赖                   |
| `@Autowired` | 多在**字段**上           | 唯一构造器时常省略                     |
| `final` 字段 | 一般不能用于被注入的字段 | 常用，引用不可再换                     |
| 本质       | 依赖注入                     | 依赖注入（装配位置不同）               |

---

## 8. 本仓库相关类（对照看代码）

- `src/main/java/com/example/mgdemoplus/websocket/DpGameRoomWebSocketHandler.java`：构造器注入 `DpGameRoomPushService`。
- `src/main/java/com/example/mgdemoplus/config/WebSocketGameRoomConfig.java`：构造器注入已装配好的 `DpGameRoomWebSocketHandler`。

WebSocket 行为说明另见：`docs/WEBSOCKET.md`。

---

*整理自日常问答，便于日后复习。*
