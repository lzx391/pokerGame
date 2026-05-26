# MGDemoPlus 本地监控栈使用说明

本文说明如何在**本地开发**时用 Docker Compose 自带的 **cAdvisor → Prometheus → Grafana** 观察容器资源占用。生产编排 **[`docker-compose-prod.yml`](../docker-compose-prod.yml)** **不包含**这些监控服务，请勿在生产环境照搬本说明。

---

## 1. 这套监控是干什么的？

| 组件 | 作用 | 本项目中访问地址 |
|------|------|------------------|
| **cAdvisor** | 采集每个 Docker 容器的 CPU、内存、网络、磁盘等指标 | [http://localhost:8080](http://localhost:8080) |
| **Prometheus** | 定期从 cAdvisor（及自身）拉取指标并存储 | [http://localhost:9090](http://localhost:9090) |
| **Grafana** | 用图表展示 Prometheus 里的数据 | [http://localhost:3000](http://localhost:3000) |

数据流向（一句话）：

```text
Docker 容器 → cAdvisor 暴露指标 → Prometheus 抓取 → Grafana 查询并画图
```

Grafana 已自动配置名为 **Prometheus** 的数据源（指向容器内 `http://prometheus:9090`），一般无需手工添加数据源。

---

## 2. 哪些 Compose 文件带监控？

| 文件 | 是否含监控 |
|------|------------|
| **[`docker-compose.yml`](../docker-compose.yml)**（仓库根目录，本地开发） | **有** `cadvisor`、`prometheus`、`grafana` |
| **[`docker-compose-prod.yml`](../docker-compose-prod.yml)**（生产） | **无** |
| **`docker-compose.hub.yml`**（Hub 镜像编排） | **无**（以该文件为准；与根目录本地 compose 不同） |

启动整栈（在仓库根目录）：

```bash
docker compose up -d
# 或首次需要构建应用镜像时：
docker compose up --build -d
```

仅启动监控三件套（应用已在跑时）：

```bash
docker compose up -d cadvisor prometheus grafana
```

---

## 3. 端口与默认账号

| 服务 | 宿主机端口 | 说明 |
|------|------------|------|
| cAdvisor | **8080** | 自带简易 Web UI，也可直接看单容器概览 |
| Prometheus | **9090** | 查询界面、Targets 是否 UP |
| Grafana | **3000** | 主看板入口 |

**Grafana 默认登录（仅本地开发，勿用于公网）：**

| 用户名 | 密码 |
|--------|------|
| `admin` | `admin` |

首次登录 Grafana 可能提示修改密码，本地可跳过或随意设置。

---

## 4. Grafana 看板：社区面板 ID 193

项目未内置 JSON 看板，推荐导入 Grafana 社区 **Docker monitoring** 面板：

1. 打开 [http://localhost:3000](http://localhost:3000) 并登录。
2. 左侧 **Dashboards** → **New** → **Import**。
3. 输入面板 ID **`193`**，选择数据源 **Prometheus**，导入即可。

**读图要点（针对 ID 193）：**

- **图例（Legend）** 一般对应 **Docker 容器名**（如 `mgdemoplus-app-1`、`mgdemoplus-mysql-1`，具体前缀取决于你执行 `docker compose` 时的项目目录名）。
- **点击图例** 可单独显示/隐藏某条曲线，便于对比 `app` 与 `mysql`、`redis`。
- 该看板**通常没有「Instance」下拉框**——多容器靠图例区分即可，属正常现象。

若导入后无数据：等 1～2 个抓取周期（默认 15 秒），并在 Prometheus [http://localhost:9090/targets](http://localhost:9090/targets) 确认 `cadvisor` 为 **UP**。

---

## 5. 建议重点看的容器与指标

在面板 193 或 cAdvisor 中，优先关注与本项目相关的容器（名称以你机器上 `docker ps` 为准）：

| 容器（示例名） | 建议关注的指标 | 简单含义 |
|----------------|----------------|----------|
| **app** | CPU %、内存 Working set / Limit | Spring Boot 是否吃满内存、`JAVA_OPTS` 是否合适 |
| **mysql** | 内存、磁盘 I/O | 连接与查询高峰时是否 OOM 或磁盘慢 |
| **redis** | 内存 | 缓存与 JTI 等 key 增多时的内存趋势 |
| **cadvisor / prometheus / grafana** | 自身 CPU、内存 | 监控栈别占满本机（见下节双核机说明） |

**Prometheus 常用自检：**

- 菜单 **Status → Targets**：`cadvisor` job 应为 **UP**。
- **Graph** 中可试查询：`container_memory_usage_bytes{name=~".*app.*"}`（名称按实际容器名调整）。

---

## 6. 压测时：JMeter 与 Grafana 一起看

本地做接口或并发压测时，建议**同时开着 Grafana（或 cAdvisor）**，对照压测时间段观察：

1. 用 JMeter（或其它压测工具）对应用发压（例如经 Nginx 或直连 **8088**，视你当前 compose 是否映射端口而定）。
2. 在 Grafana 面板 193 中看 **app** 容器的 CPU、内存、网络是否随 QPS 上升。
3. 若 **mysql**、**redis** 曲线同步升高，可能是 DB/缓存成为瓶颈；若 mainly **app** 升高，优先查应用线程、连接池、GC。

这样能把「响应变慢」和「哪一层资源先顶满」对应起来，比只看 JMeter 聚合报告更直观。

---

## 7. 双核（2 CPU）机器与资源限制说明

[`docker-compose.yml`](../docker-compose.yml) 为各服务设置了 **`cpus` / `mem_limit`**（仅本地 compose）。与监控相关的片段大致为：

| 服务 | 内存上限（约） | CPU 上限（约） |
|------|----------------|----------------|
| cadvisor | 96m | 0.1 |
| prometheus | 128m | 0.2 |
| grafana | 128m | 0.2 |
| app | 1200m | 1.2 |
| mysql | 450m | 0.5 |
| redis | 64m | 0.2 |

在 **2 核 CPU** 的笔记本或小 VPS 上，**业务容器 + 监控** 的 CPU 上限之和可能**超过 2**（Docker 的 `cpus` 是调度权重上限，不是严格独占）。可能出现：

- 整机卡顿、风扇响、压测时 Grafana 查询变慢；
- 监控容器与 **app** 争抢 CPU，指标波动大。

**建议：**

- 监控栈**仅用于本地开发、问题排查**，不需要时可用  
  `docker compose stop cadvisor prometheus grafana` 停掉监控，减轻机器压力。
- 内存紧张时优先保证 **app / mysql / redis**；监控三件套合计约 **350MB** 量级，小内存机器请心里有数。
- **生产环境**使用 [`docker-compose-prod.yml`](../docker-compose-prod.yml)，**不要**部署本监控栈；生产指标应使用云厂商或独立监控方案。

---

## 8. 配置文件位置（进阶）

| 路径 | 说明 |
|------|------|
| [`docker/prometheus/prometheus.yml`](../docker/prometheus/prometheus.yml) | Prometheus 抓取 `cadvisor:8080` |
| [`docker/grafana/provisioning/datasources/prometheus.yml`](../docker/grafana/provisioning/datasources/prometheus.yml) | Grafana 预置 Prometheus 数据源 |

修改 `prometheus.yml` 后，可执行：

```bash
docker compose restart prometheus
```

---

## 9. 常见问题

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| Grafana 无曲线 | Prometheus 未抓到 cAdvisor | 看 [9090/targets](http://localhost:9090/targets) 中 `cadvisor` 是否 UP |
| 图例名称看不懂 | 为 Docker Compose 自动生成的容器名 | 用 `docker ps` 对照；点击图例隔离单容器 |
| 8080 端口冲突 | 本机已有程序占用 8080 | 改 compose 中 `cadvisor` 的 `ports` 映射，或停掉冲突进程 |
| 生产 compose 没有 Grafana | 设计如此 | 仅本地 [`docker-compose.yml`](../docker-compose.yml) 提供监控 |

---

## 10. 相关文档

- 项目主 README「监控」小节：[README.md](../README.md)（快速入口与端口）
- Docker 编排总览：[DOCKER.md](DOCKER.md)
