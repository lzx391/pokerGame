# RabbitMQ Java 八股 — 面试复习笔记

> **核对日期**：2026-06-24  
> **适用场景**：Java 后端面试、Spring Boot 消息队列模块、分布式异步解耦设计  
> **技术栈参考**：RabbitMQ 3.x、Spring AMQP 3.x、Spring Boot 3.x、Java 17+

---

## 目录

1. [基础概念](#1-基础概念)
2. [Exchange 类型](#2-exchange-类型)
3. [消息模型与路由键](#3-消息模型与路由键)
4. [消息可靠性](#4-消息可靠性)
5. [死信队列 DLX](#5-死信队列-dlx)
6. [延迟消息](#6-延迟消息)
7. [幂等与重复消费](#7-幂等与重复消费)
8. [顺序消息](#8-顺序消息)
9. [高可用与集群](#9-高可用与集群)
10. [Spring AMQP / Spring Boot](#10-spring-amqp--spring-boot)
11. [与 Kafka 对比](#11-与-kafka-对比)
12. [常见面试题 Q&A](#12-常见面试题-qa)
13. [MGDemoPlus 项目关联](#13-mgdemoplus-项目关联)

---

## 1. 基础概念

### 1.1 AMQP 是什么

**AMQP**（Advanced Message Queuing Protocol，高级消息队列协议）是一套**开放标准**的消息中间件协议，定义了：

| 维度 | 说明 |
|------|------|
| 网络层 | 基于 TCP 的二进制帧协议 |
| 语义层 | 消息、Exchange、Queue、Binding、ACK 等概念 |
| 行为层 | 生产者发布、Broker 路由、消费者订阅 |

RabbitMQ 是 AMQP 0-9-1 的**主流实现**之一（还有 Apache Qpid 等）。面试常说「RabbitMQ 实现了 AMQP」，本质是协议 + 实现的关系。

**口诀**：AMQP 定规矩，RabbitMQ 做实现。

### 1.2 核心组件

```
Producer ──publish──▶ Exchange ──route──▶ Queue ──consume──▶ Consumer
                          ▲
                     Binding（绑定规则）
```

| 组件 | 英文 | 职责 |
|------|------|------|
| **Broker** | 消息代理 | RabbitMQ 服务器进程，接收、存储、转发消息 |
| **Exchange** | 交换机 | 接收生产者消息，按类型 + Binding 规则路由到 Queue |
| **Queue** | 队列 | 存储消息，等待消费者拉取 |
| **Binding** | 绑定 | Exchange 与 Queue 之间的路由关系（含 routing key / headers 匹配规则） |
| **Virtual Host** | 虚拟主机 | 逻辑隔离单元，类似「命名空间」，独立权限与资源 |

**易错点**：消息**不会**直接发到 Queue，必须先经过 Exchange。新手常写 `queue.publish()` 思维，RabbitMQ 里是 `exchange.publish()`。

### 1.3 Virtual Host（vhost）

- 默认 vhost：`/`
- 不同 vhost 之间 **Queue、Exchange、用户权限完全隔离**
- 多租户、多环境（dev/test/prod）常用不同 vhost 划分
- 连接 URI 示例：`amqp://user:pass@host:5672/dev_vhost`

### 1.4 Connection 与 Channel

| 概念 | 说明 |
|------|------|
| **Connection** | 客户端与 Broker 之间的 TCP 连接，创建开销大（握手、认证、心跳） |
| **Channel** | 在 Connection 之上开辟的**轻量级逻辑通道**，共享 TCP，独立 AMQP 会话 |

**为什么 Channel 轻量？**

1. Connection 建立需要 TCP 三次握手 + AMQP 协议握手 + 认证，成本高
2. Channel 只是在已有 Connection 上分配一个 channel number，复用同一 TCP 链路
3. 多线程场景：**每个线程用独立 Channel**，而不是每个线程新建 Connection（Connection 非线程安全，Channel 在正确使用下可 per-thread）
4. Broker 端也为 Channel 维护独立的投递标签（delivery tag）、confirm 序列

**口诀**：一 Connection 多 Channel，线程独享 Channel。

**Java 示例（概念）**：

```java
ConnectionFactory factory = new ConnectionFactory();
factory.setHost("localhost");
try (Connection connection = factory.newConnection();
     Channel channel = connection.createChannel()) {
    // 声明、发布、消费都在 channel 上操作
}
```

---

## 2. Exchange 类型

Exchange 类型决定**如何把消息路由到 Queue**。四种内置类型：

| 类型 | 路由依据 | 典型场景 |
|------|----------|----------|
| **direct** | routing key **精确匹配** binding key | 点对点任务分发、按业务类型路由 |
| **fanout** | **忽略** routing key，广播到所有绑定 Queue | 广播通知、缓存失效、日志采集 |
| **topic** | routing key 与 binding key **模式匹配**（`*` 一词，`#` 多词） | 日志分级、多订阅者按主题过滤 |
| **headers** | 消息 headers 键值对匹配（x-match: all/any） | 复杂条件路由（用得少，性能不如 topic） |

### 2.1 direct

- 发送时指定 `routingKey`，如 `"order.created"`
- Queue 绑定 Exchange 时指定相同 binding key
- **完全相等**才路由

```java
channel.exchangeDeclare("order.direct", BuiltinExchangeType.DIRECT, true);
channel.queueDeclare("order.create.queue", true, false, false, null);
channel.queueBind("order.create.queue", "order.direct", "order.created");

channel.basicPublish("order.direct", "order.created", props, body);
// routingKey 必须 == "order.created" 才会进 order.create.queue
```

### 2.2 fanout

- routing key 传空字符串即可
- 所有绑定的 Queue 各收一份副本

```java
channel.exchangeDeclare("notify.fanout", BuiltinExchangeType.FANOUT, true);
channel.queueBind("email.queue", "notify.fanout", "");
channel.queueBind("sms.queue", "notify.fanout", "");
channel.basicPublish("notify.fanout", "", props, body); // 两队列都收到
```

### 2.3 topic

Binding key 与 routing key 均为 `.` 分隔的词，如 `log.error.payment`：

| 通配符 | 含义 |
|--------|------|
| `*` | 匹配**恰好一个词** |
| `#` | 匹配**零个或多个词** |

示例：

| binding key | 能匹配的 routing key |
|-------------|---------------------|
| `log.*` | `log.error`、`log.info` |
| `log.#` | `log`、`log.error`、`log.error.payment` |
| `*.error` | `app.error`、`db.error` |

```java
channel.exchangeDeclare("log.topic", BuiltinExchangeType.TOPIC, true);
channel.queueBind("error.queue", "log.topic", "*.error");
channel.basicPublish("log.topic", "app.error", props, body); // 命中
```

### 2.4 headers

- 几乎不用 routing key，靠消息 headers + `x-match`（all 全匹配 / any 任一匹配）
- 灵活但性能较差，面试知道即可

```java
Map<String, Object> headers = Map.of("type", "pdf", "x-match", "all");
channel.queueBind("pdf.queue", "headers.exchange", "", headers);
```

### 2.5 默认 Exchange

- 名称为空字符串 `""` 的 **direct** Exchange
- routing key = queue name 时直达对应 Queue（`basicPublish("", "myQueue", ...)`）
- 简单点对点可用，生产环境建议显式声明 Exchange

---

## 3. 消息模型与路由键

### 3.1 完整流转

```
1. Producer 连接 Broker，打开 Channel
2. 声明 Exchange、Queue、Binding（可提前声明，也可由管理端/消费者声明）
3. Producer 发消息到 Exchange，携带 routing key（及 headers、properties）
4. Exchange 按类型 + Binding 规则选择 Queue
5. Queue 持久化消息（若配置了 durable）
6. Consumer 订阅 Queue，Broker 推送或 Consumer 拉取
7. Consumer 处理完毕发送 ACK（或 NACK / Reject）
8. Broker 删除或重新入队
```

### 3.2 routing key vs binding key

| 名称 | 谁设置 | 作用 |
|------|--------|------|
| **routing key** | **生产者**发布消息时指定 | Exchange 路由的「入参」 |
| **binding key** | **管理员/代码**绑定 Queue 到 Exchange 时指定 | Exchange 路由的「规则」 |

- **direct**：routing key == binding key
- **topic**：routing key 匹配 binding key 模式
- **fanout**：两者均可忽略
- **headers**：看 headers，不看 key

**易错点**：routing key 和 binding key **名字相似、角色不同** — 一个是消息上的标签，一个是绑定关系上的过滤器。

### 3.3 Message 结构（AMQP 概念）

| 部分 | 内容 |
|------|------|
| **properties** | contentType、deliveryMode（持久化）、messageId、timestamp、headers |
| **body** | 字节数组，JSON / Protobuf / 文本均可 |

---

## 4. 消息可靠性

消息可靠性要回答：**消息会不会丢？会不会重复？** 需从三个环节分别加固。

```
生产者 ──▶ Broker（Exchange/Queue） ──▶ 消费者
   ①              ②                      ③
```

### 4.1 生产者确认（Publisher Confirm）

**问题**：消息发到 Broker 后，网络闪断，生产者不知道是否成功。

**方案 A：事务（Transaction）—— 不推荐**

```java
channel.txSelect();
channel.basicPublish(...);
channel.txCommit(); // 或 txRollback()
```

| 对比项 | 事务 | Confirm |
|--------|------|---------|
| 机制 | AMQP tx 同步提交 | 异步 / 同步 confirm |
| 性能 | **差**（吞吐下降一个数量级） | **好** |
| 使用 | 老项目、面试对比 | **生产首选** |

**方案 B：Publisher Confirm（推荐）**

1. `channel.confirmSelect()` 开启 confirm 模式
2. 消息发布后 Broker 返回 **ACK**（成功）或 **NACK**（失败）
3. 同步：`waitForConfirms()` / 异步：ConfirmCallback

```java
channel.confirmSelect();
channel.addConfirmListener((deliveryTag, multiple) -> {
    // ACK：消息已被 Broker 接收（通常指到达 Exchange；mandatory 时还可追踪是否入队）
}, (deliveryTag, multiple) -> {
    // NACK：重试或落库
});
channel.basicPublish("ex", "rk", props, body);
```

**口诀**：生产用 Confirm，别用事务。

### 4.2 mandatory 与 return 回调

| 参数 | 含义 |
|------|------|
| **mandatory=true** | 消息无法路由到任何 Queue 时，**退回**给生产者（ReturnCallback），而不是静默丢弃 |
| **mandatory=false**（默认） | 路由失败直接丢弃 |

配合 `ReturnCallback` 可发现「Exchange 有、Binding 错」的配置问题。

```java
channel.addReturnListener((replyCode, replyText, exchange, routingKey, properties, body) -> {
    log.error("消息被退回: exchange={}, rk={}", exchange, routingKey);
});
channel.basicPublish("ex", "wrong.key", MessageProperties.PERSISTENT_TEXT_PLAIN, body);
// 若 mandatory=true 且无匹配 Queue → 触发 return
```

### 4.3 持久化三件套

| 对象 | 配置 | 说明 |
|------|------|------|
| **Exchange** | `durable=true` | 重启后 Exchange 元数据仍在 |
| **Queue** | `durable=true` | 重启后 Queue 元数据仍在 |
| **Message** | `deliveryMode=2`（PERSISTENT） | 消息体写入磁盘（配合队列持久化） |

```java
// Exchange / Queue 声明
channel.exchangeDeclare("ex", BuiltinExchangeType.DIRECT, true);  // durable
channel.queueDeclare("q", true, false, false, null);              // durable

// 消息持久化
AMQP.BasicProperties props = MessageProperties.PERSISTENT_TEXT_PLAIN;
channel.basicPublish("ex", "rk", props, body);
```

**易错点**：只持久化 Queue 不持久化 Message，重启后 Queue 在、消息空。三者要一起配才叫「持久化链路完整」。

**注意**：持久化有磁盘 IO 成本；非关键消息可用 `deliveryMode=1`（非持久）换性能。

### 4.4 消费者 ACK

| 模式 | 配置 | 行为 |
|------|------|------|
| **自动 ACK** | `autoAck=true` | Broker 一推送就认为成功，**消费者处理中宕机 → 消息丢失** |
| **手动 ACK** | `autoAck=false` | 业务处理完再 `basicAck`，失败可 `basicNack` / `basicReject` |

```java
channel.basicConsume("q", false, (consumerTag, delivery) -> {
    try {
        process(delivery.getBody());
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
    } catch (Exception e) {
        // requeue=false → 进死信；requeue=true → 重新入队（可能死循环）
        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
    }
}, consumerTag -> {});
```

### 4.5 prefetch（QoS）

```java
channel.basicQos(1); // 该 Channel 上最多 1 条未 ACK 消息
```

| 作用 | 说明 |
|------|------|
| 公平分发 | 慢消费者不会堆积大量未处理消息 |
| 背压 | 控制内存与处理节奏 |
| 与 ACK 配合 | prefetch 满时 Broker 暂停推送 |

**面试常问**：prefetch 设太大 → 一条消费者拖死仍占很多消息；设 1 吞吐可能下降，需压测权衡。

### 4.6 requeue

- `basicReject(tag, requeue=true)` / `basicNack(..., requeue=true)`：消息**重新入队**
- 风险： poison message 无限重试 → 必须配合**重试次数**或 **DLX**
- `requeue=false`：消息丢弃或进入死信队列（若配置了 DLX）

### 4.7 可靠性 checklist

| 环节 | 措施 |
|------|------|
| 生产 | Confirm + mandatory + 持久化 + 本地消息表（极端场景） |
| Broker | 集群 / 镜像或 Quorum Queue、磁盘告警 |
| 消费 | 手动 ACK + prefetch + 幂等 + DLX |

---

## 5. 死信队列 DLX

### 5.1 什么是死信（Dead Letter）

消息变成「死信」的常见条件：

| 触发条件 | 说明 |
|----------|------|
| 消息被拒绝 | `basicReject` / `basicNack` 且 `requeue=false` |
| 消息 TTL 过期 | 在 Queue 中超过 `x-message-ttl` |
| 队列长度超限 | 超过 `x-max-length`，头部消息被挤出 |
| 队列 TTL 过期 | 队列 `x-expires`（少用） |

### 5.2 DLX 配置

在**业务 Queue** 上声明参数，指向死信 Exchange：

```java
Map<String, Object> args = new HashMap<>();
args.put("x-dead-letter-exchange", "dlx.exchange");
args.put("x-dead-letter-routing-key", "dlx.routing.key");
channel.queueDeclare("biz.queue", true, false, false, args);
```

死信消息会被 RabbitMQ **重新发布**到 `dlx.exchange`，routing key 为 `dlx.routing.key`，再路由到绑定的 DLQ。

### 5.3 典型用途

| 用途 | 做法 |
|------|------|
| **失败兜底** | 消费失败 N 次 → NACK requeue=false → DLQ 人工排查 |
| **延迟队列** | 业务 Queue 设 TTL + DLX，过期后进入真正消费 Queue（见第 6 节） |
| **审计归档** | 所有无法处理的消息统一进 DLQ 落库 |
| **降级** | 主 Queue 满了，溢出消息进 DLQ |

**口诀**：requeue=false 是进 DLX 的前提（拒绝场景下）。

---

## 6. 延迟消息

### 6.1 TTL + DLX（原生，无插件）

**原理**：

```
Producer → [delay.queue] (设 x-message-ttl=30s, x-dead-letter-exchange=real.ex)
                ↓ TTL 到期
           real.queue ← Consumer 消费
```

```java
// 延迟队列：消息 TTL 30 秒，到期死信到 real.exchange
Map<String, Object> delayArgs = Map.of(
    "x-message-ttl", 30000,
    "x-dead-letter-exchange", "real.exchange",
    "x-dead-letter-routing-key", "real.key"
);
channel.queueDeclare("delay.queue", true, false, false, delayArgs);
```

**缺点**：

- 同一队列内消息 TTL 不同则**队头阻塞**（前一条未过期，后一条即使到期也要等）
- 精度受扫描间隔影响，非精确调度

### 6.2 延迟插件（rabbitmq_delayed_message_exchange）

- 安装 `rabbitmq_delayed_message_exchange` 插件
- 使用 `x-delayed-message` 类型 Exchange
- 发布时在 headers 设 `x-delay`（毫秒）

```java
// 声明需插件支持
// channel.exchangeDeclare("delayed.ex", "x-delayed-message", true, false,
//     Map.of("x-delayed-type", "direct"));

Map<String, Object> headers = Map.of("x-delay", 60000); // 延迟 60s
AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
    .headers(headers).build();
channel.basicPublish("delayed.ex", "rk", props, body);
```

### 6.3 方案对比

| 方案 | 优点 | 缺点 |
|------|------|------|
| TTL + DLX | 无需插件、原生支持 | 队头阻塞、精度一般、不同延迟需多队列 |
| 延迟插件 | 精确延迟、API 简单 | 需装插件、集群要一致 |
| 定时任务 + DB | 不依赖 MQ | 吞吐低、非实时 |
| Redis ZSet / 时间轮 | 高性能延迟 | 另维护组件 |

**面试答法**：短延迟、量不大用 TTL+DLX；复杂延迟调度用插件；MGDemoPlus 量级可先 TTL+DLX 或 Redis 延迟队列。

---

## 7. 幂等与重复消费

### 7.1 为什么会重复

| 原因 | 说明 |
|------|------|
| 生产者重试 | Confirm 超时重发 |
| Broker 重投 | 消费者 ACK 前宕机 |
| 网络抖动 | ACK 丢失，Broker 认为未确认 |

**结论**：MQ **至少一次（At Least Once）** 语义下，重复不可避免，必须在**业务层幂等**。

### 7.2 业务层方案

| 方案 | 实现要点 |
|------|----------|
| **数据库唯一键** | `message_id` / 业务单号 UNIQUE，重复插入失败即视为已处理 |
| **状态机** | 订单状态只允许 `CREATED → PAID`，重复 PAID 消息跳过 |
| **Redis 去重** | `SETNX msg:{id} 1 EX 86400`，失败则丢弃 |
| **乐观锁** | `UPDATE ... WHERE version=?`，版本不对则放弃 |
| **消费日志表** | 先 INSERT consume_log(message_id)，主键冲突则不再执行业务 |

```java
// Redis 去重示例
String key = "mq:dedup:" + messageId;
Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofDays(1));
if (Boolean.FALSE.equals(ok)) {
    channel.basicAck(deliveryTag, false); // 重复消息直接 ACK
    return;
}
doBusiness();
channel.basicAck(deliveryTag, false);
```

### 7.3 与 ACK 的顺序

**推荐**：业务幂等校验 → 执行业务 → ACK。  
**反模式**：先 ACK 再执行业务（宕机即丢消息）。

---

## 8. 顺序消息

### 8.1 RabbitMQ 的顺序保证

- **单 Queue + 单 Consumer（单线程处理）**：FIFO，天然有序
- **单 Queue + 多 Consumer**：并发消费，**顺序无法保证**
- **多 Queue**：分片后各分片内有序，全局无序

### 8.2 hash 分区策略

按业务 key（如 `orderId`）hash 到固定 Queue，每个 Queue 一个 Consumer：

```
orderId % N → queue-0 .. queue-(N-1)
每个 queue 单 consumer → 同一 orderId 的消息有序
```

### 8.3 局限

| 局限 | 说明 |
|------|------|
| 热点 key | 某 orderId 流量极大，对应 Queue 成为瓶颈 |
| 扩缩容 | 增加 Queue 数需重新 hash，历史顺序语义变化 |
| 失败重试 | 重试消息可能乱序到达 |
| 对比 Kafka | Kafka 分区内有序 + 消费组，更适合**日志型顺序流** |

**面试答法**：RabbitMQ 顺序消息靠「单队列单消费者」或 hash 分区；强顺序 + 高吞吐日志场景优先考虑 Kafka。

---

## 9. 高可用与集群

### 9.1 集群基本概念

- 多个 RabbitMQ 节点组成**集群**，共享元数据（Exchange、Queue 定义等）
- 默认 Queue 消息只存在于**声明它的节点**（非 HA 时节点挂掉消息可能不可用）

### 9.2 镜像队列（Classic Mirrored Queue）— 传统 HA

- 队列镜像到多个节点，**主从复制**
- 缺点：同步复制影响性能；Classic 队列在 3.13+ 已**弃用**镜像，转向 Quorum

### 9.3 Quorum Queue（仲裁队列）— 推荐

- 基于 **Raft 共识**，3 节点奇数副本
- 强一致、数据安全，官方推荐替代镜像 Classic Queue
- 声明：`queueDeclare(..., arguments: {"x-queue-type": "quorum"})`

```java
Map<String, Object> args = Map.of("x-queue-type", "quorum");
channel.queueDeclare("ha.queue", true, false, false, args);
```

### 9.4 面试要点

| 问题 | 简答 |
|------|------|
| 节点挂了怎么办？ | Quorum / 镜像保证消息冗余；客户端连接列表 failover |
| 脑裂？ | Quorum Raft 选主；旧版镜像需 `pause_minority` 等策略 |
| 流式插件 | RabbitMQ Stream（类似 Kafka log），大数据量新选项 |

---

## 10. Spring AMQP / Spring Boot

### 10.1 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### 10.2 常用配置（application.yml）

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    publisher-confirm-type: correlated   # 开启 Confirm
    publisher-returns: true              # 开启 Return
    template:
      mandatory: true
    listener:
      simple:
        acknowledge-mode: manual           # 手动 ACK
        prefetch: 10
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000ms
```

### 10.3 RabbitTemplate 发送

```java
@Autowired
private RabbitTemplate rabbitTemplate;

public void send(OrderEvent event) {
    rabbitTemplate.convertAndSend("order.exchange", "order.created", event);
}

// Confirm / Return 回调
@PostConstruct
public void init() {
    rabbitTemplate.setConfirmCallback((correlation, ack, cause) -> {
        if (!ack) { /* 补偿 */ }
    });
    rabbitTemplate.setReturnsCallback(returned -> {
        log.error("returned: {}", returned.getMessage());
    });
}
```

### 10.4 @RabbitListener 消费

```java
@RabbitListener(queues = "order.create.queue")
public void onMessage(OrderEvent event, Channel channel,
                      @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
    try {
        orderService.handle(event);
        channel.basicAck(tag, false);
    } catch (BusinessException e) {
        channel.basicNack(tag, false, false); // 进 DLQ
    }
}
```

### 10.5 声明 Exchange / Queue / Binding

```java
@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange("order.direct", true, false);
    }

    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable("order.create.queue")
            .deadLetterExchange("dlx.exchange")
            .deadLetterRoutingKey("order.dlx")
            .build();
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderCreateQueue())
            .to(orderExchange()).with("order.created");
    }
}
```

### 10.6 JSON 转换

```java
@Bean
public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
}

// RabbitTemplate 自动注入 converter，@RabbitListener 直接收 POJO
```

### 10.7 异常处理与 Retry

| 机制 | 说明 |
|------|------|
| `spring.rabbitmq.listener.simple.retry.*` | 容器内重试（默认 Reject 且不 requeue 可进 DLQ） |
| `ErrorHandler` / `ConditionalRejectingErrorHandler` | 区分业务异常与致命错误 |
| `@RabbitListener` + 自定义 `RabbitListenerErrorHandler` | 细粒度控制 |

**易错点**：Spring Retry 重试 exhausted 后默认 **reject 且不 requeue**，需配 DLX 接住，否则消息丢失。

---

## 11. 与 Kafka 对比

| 维度 | RabbitMQ | Kafka |
|------|----------|-------|
| 定位 | **消息 broker**，智能路由（Exchange） | **分布式 commit log**，持久化流 |
| 协议 | AMQP | 自定义二进制协议 |
| 消息模型 | Queue 消费后通常删除（可 TTL） | 日志 retention，可重复读 |
| 路由 | Exchange 类型丰富 | Topic + Partition |
| 吞吐 | 万级～十万级/s（视场景） | 百万级/s 常见 |
| 顺序 | 单队列有序 | **分区内**有序 |
| 延迟 | 毫秒级，适合 RPC 异步化 | 毫秒～秒，适合大数据管道 |
| 消费模式 | push 为主 | pull 为主 |
| 事务 / 精确一次 | 较弱，靠业务幂等 | 事务 + idempotent producer（仍复杂） |

### 何时选 RabbitMQ（八股标准答法）

1. **任务队列、异步解耦**：下单后发邮件、发短信
2. **复杂路由**：direct / topic 多订阅者
3. **低延迟、消息较短**：请求响应式异步、RPC（Reply-To 队列）
4. **团队已有 AMQP 生态**、运维熟悉 RabbitMQ Management
5. **消息确认后立即删除**，不需要长期 replay

### 何时选 Kafka

1. **日志采集、行为埋点、CDC**
2. **大数据量、高吞吐**流处理
3. **事件溯源、多次回放**同一 stream
4. **Stream 计算**（Flink / Spark Streaming 对接）

**口诀**：要路由选 Rabbit，要日志选 Kafka。

---

## 12. 常见面试题 Q&A

### Q1：RabbitMQ 和 AMQP 什么关系？

**答**：AMQP 是协议标准，RabbitMQ 是该协议的开源实现。AMQP 定义了 Exchange、Queue、Binding、Channel 等模型与帧格式。

### Q2：消息怎么从生产者到消费者？

**答**：Producer 发布到 Exchange 并带 routing key → Exchange 按类型和 Binding 路由到 Queue → Consumer 订阅 Queue 消费并 ACK。

### Q3：为什么用 Channel 而不是多个 Connection？

**答**：Connection 是昂贵 TCP 长连接；Channel 是轻量逻辑通道，共享 Connection，适合多线程每线程一 Channel，减少握手开销。

### Q4：direct 和 topic 区别？

**答**：direct 要求 routing key 与 binding key **完全相等**；topic 支持 `*`、`#` 通配符模式匹配，适合多级主题。

### Q5：fanout 和 direct 区别？

**答**：fanout 忽略 routing key，广播到所有绑定 Queue；direct 按 key 精确路由到一个或多个 binding 相同的 Queue。

### Q6：如何保证消息不丢？

**答**：生产端 Confirm + 持久化三件套 + mandatory；Broker 端集群/Quorum；消费端手动 ACK + 幂等 + DLX 兜底。

### Q7：Confirm 和事务区别？

**答**：事务同步阻塞，性能差；Confirm 异步确认，吞吐高。生产环境用 Confirm。

### Q8：mandatory 有什么用？

**答**：消息无法路由到 Queue 时退回生产者 ReturnCallback，避免静默丢失，便于发现 Binding 配置错误。

### Q9：自动 ACK 和手动 ACK？

**答**：自动 ACK 一推送即确认，处理中宕机会丢消息；手动 ACK 业务成功后再确认，可靠但需防重复消费。

### Q10：prefetch 是什么？

**答**：限制未 ACK 消息数量，实现公平分发和背压。`basicQos(n)` 设置。

### Q11：什么是死信？怎么进 DLQ？

**答**：被拒绝且不 requeue、TTL 过期、队列满被挤出的消息。业务 Queue 配置 `x-dead-letter-exchange` 转发到死信 Exchange。

### Q12：延迟消息怎么做？

**答**：TTL+DLX（原生，注意队头阻塞）或 `rabbitmq_delayed_message_exchange` 插件（headers 设 x-delay）。

### Q13：重复消费怎么处理？

**答**：MQ 至少一次语义，业务幂等：唯一键、Redis SETNX、状态机、消费日志表。

### Q14：如何保证顺序？

**答**：单 Queue 单 Consumer；或按 key hash 到固定 Queue 各单 Consumer。多 Consumer 并发无法保证顺序。

### Q15：镜像队列和 Quorum Queue？

**答**：镜像队列 Classic 主从复制，已弃用倾向；Quorum Queue 基于 Raft，强一致，官方 HA 推荐。

### Q16：Spring 如何发消息？

**答**：`RabbitTemplate.convertAndSend(exchange, routingKey, body)`，配 `publisher-confirm-type` 和 `MessageConverter`。

### Q17：@RabbitListener 如何手动 ACK？

**答**：`acknowledge-mode: manual`，方法注入 `Channel` 和 `DELIVERY_TAG`，成功 `basicAck`，失败 `basicNack`。

### Q18：RabbitMQ 和 Kafka 怎么选？

**答**：任务队列、复杂路由、低延迟短消息用 RabbitMQ；日志流、高吞吐、回放用 Kafka。

### Q19：poison message 怎么办？

**答**：限制重试次数，失败后 requeue=false 进 DLQ，人工或告警处理；勿无限 requeue。

### Q20：Virtual Host 作用？

**答**：逻辑隔离 Exchange/Queue/权限，多租户或多环境划分。

---

## 13. MGDemoPlus 项目关联

### 13.1 当前状态

经仓库检索（`rabbitmq`、`RabbitTemplate`、`@RabbitListener`、`spring-amqp` 等关键词），**MGDemoPlus 当前未接入 RabbitMQ**。

项目现有异步/实时能力：

| 能力 | 实现 |
|------|------|
| 局内实时 | 原生 WebSocket + 内存连接表 |
| 大厅对齐 | `DpRoomLobbyReconcileScheduler` 定时任务 |
| 权限缓存 | Redis |
| 快速匹配 | 内存队列（`quickmatch` 模块） |

### 13.2 若接入 RabbitMQ — 检查清单

| 步骤 | 内容 |
|------|------|
| 1. 依赖 | `pom.xml` 增加 `spring-boot-starter-amqp` |
| 2. 配置 | `.env` / `application.yml`：`RABBITMQ_HOST`、`PORT`、`USER`、`PASSWORD`、`VHOST` |
| 3. Docker | `docker-compose.yml` 增加 `rabbitmq:3-management` 服务（15672 管理台） |
| 4. 配置类 | `config/DpRabbitConfig.java`：Exchange、Queue、Binding、JSON Converter |
| 5. 生产者 | 房间事件、社交通知等从同步调用改为 `RabbitTemplate` |
| 6. 消费者 | `@RabbitListener` + 手动 ACK + 幂等（Redis / DB） |
| 7. 可靠性 | Confirm、持久化、DLX、`publisher-returns` |
| 8. 安全 | 新 Broker 端口按需加入防火墙；**不**加入 JWT 白名单（走 AMQP 认证） |
| 9. 单实例约束 | 当前房间状态在**单机内存**，跨实例广播需先解决房间状态外置或 sticky session |
| 10. 监控 | Management 插件 / Prometheus exporter |

### 13.3 潜在接入场景（设计参考）

| 场景 | 说明 |
|------|------|
| 异步通知 | 好友申请、私信离线推送（减轻 SSE 长连接压力） |
| 牌谱归档 | 对局结束异步写 `history`，削峰 |
| NPC LLM 调用 | 大模型决策异步化，避免阻塞行动超时 |
| 审计日志 | 管理操作发 fanout 供多订阅者 |

**注意**：接入前需评估 MGDemoPlus **单进程内存房间**架构；MQ 适合跨服务解耦，同进程内简单异步可用 `@Async` 或 `ApplicationEventPublisher` 先行。

---

## 附录：速记口诀

| 主题 | 口诀 |
|------|------|
| 路由 | 生产到 Exchange，Binding 定 Queue |
| 四 Exchange | direct 精确，fanout 广播，topic 通配，headers 头匹配 |
| 可靠性 | Confirm 别事务，持久三件套，手动 ACK 加 prefetch |
| 死信 | reject 不 requeue，TTL 过期，DLX 来接 |
| 延迟 | TTL 加 DLX 原生；精调度上插件 |
| 幂等 | 至少一次必重复，业务唯一键 / Redis 挡 |
| 顺序 | 单队单消费，hash 分区勉强够 |
| 选型 | 要路由 Rabbit，要日志 Kafka |

---

## 附录：易错点汇总

1. 以为消息直接进 Queue — 必须经过 Exchange  
2. 只 durable Queue 不设 message persistent — 重启消息仍丢  
3. 自动 ACK 当可靠 — 处理中断即丢  
4. requeue=true 无限重试 — poison message 打满队列  
5. TTL 延迟忽略队头阻塞 — 不同延迟需多队列或插件  
6. 先 ACK 后业务 — 宕机丢消息  
7. Spring Retry 不配 DLX — 重试耗尽消息可能被丢弃  
8. 多 Consumer 还要求全局顺序 — RabbitMQ 做不到，换设计或换 Kafka  

---

*文档版本：v1.0 · 配合视频：`docs/notes/rabbitmq-java-bagu/video/`*
