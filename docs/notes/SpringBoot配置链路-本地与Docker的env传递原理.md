# SpringBoot 配置链路：本地 vs Docker 的 env 传递原理

## 核心结论

> **Docker 容器是隔离的墙，compose 的 `environment` 是墙上唯一的门，没写在门上的变量进不去。**

---

## 第一层：application.yml 的占位符是怎么找值的

```yaml
mgdemoplus:
  experimental-deck-preset-password: ${EXPERIMENTAL_DECK_PRESET_PASSWORD:}
```

`${VAR_NAME:默认值}` Spring Boot 启动时按以下**优先级**查找：

| 优先级 | 来源 | 示例 |
|--------|------|------|
| 1（最高） | 命令行参数 | `--EXPERIMENTAL_DECK_PRESET_PASSWORD=xxx` |
| 2 | JVM 系统属性 | `-DEXPERIMENTAL_DECK_PRESET_PASSWORD=xxx` |
| 3 | **操作系统环境变量** | 当前 OS 的 `EXPERIMENTAL_DECK_PRESET_PASSWORD` |
| 4（最低） | 冒号后面的默认值 | 空字符串 `""` |

**关键：Spring Boot 只看"Java 进程所在的操作系统"的环境变量。** 它不知道 `.env` 文件的存在，不知道 docker-compose 的存在。

---

## 第二层：本地开发 vs Docker 部署

### 本地开发（Java 直接跑在本机）

```
┌─────────────────────────┐
│      Windows 本机        │
│  .env 或 IDE 设的变量     │
│         ↓               │
│  ┌───────────────────┐  │
│  │   Java 进程        │  │ ← Spring 直接读本机环境变量 ✅
│  │   application.yml  │  │
│  └───────────────────┘  │
└─────────────────────────┘
```

- 本机 .env（或 IDE Run Configuration）把变量注入当前 OS 环境
- Java 进程直接跑在本机 → 直接读到
- **没有中间层**

### Docker 部署（Java 跑在容器里）

```
┌─────────────────────────────────┐
│           Linux 宿主机           │
│  ┌───────────────────────────┐  │
│  │  .env 文件（躺磁盘上）      │  │
│  │  EXP_PWD=rig              │  │
│  └───────────────────────────┘  │
│  ┌───────────────────────────┐  │
│  │  Docker 容器 = 独立 OS     │  │ ← 隔离环境的墙
│  │  ┌─────────────────────┐  │  │
│  │  │  Java 进程            │  │  │ ← 只看容器内的环境变量
│  │  │  application.yml     │  │  │   宿主机有什么完全未知
│  │  └─────────────────────┘  │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

- 宿主机的 `.env` 文件和宿主机的环境变量 → **容器内的 Java 看不到**
- Docker 容器是完整隔离的操作系统级环境
- `.env` 文件只是个磁盘文件，不是容器的环境变量

---

## 第三层：docker-compose 是唯一的搬运工

```yaml
services:
  app:
    environment:
      JWT_SECRET: ${JWT_SECRET:-}          # 搬运：宿主机 .env → 容器 ✅
      TZ: Asia/Shanghai                    # 直接写死 ✅
      # 没写 EXPERIMENTAL_DECK_PRESET_PASSWORD —— 容器里就没有 ❌
```

- docker-compose 启动时会**读取同目录的 `.env` 文件**，替换自身 `${}` 占位符
- 然后把替换后的值注入容器的环境变量
- **没写在 `environment:` 里的 → 不会出现在容器中 → Java 永远读不到**

---

## 一条完整链路的正反面

```
变量: EXPERIMENTAL_DECK_PRESET_PASSWORD=rig

✅ 正常链路（完整）：
  .env 文件        →  compose ${}   →  容器 env   →  application.yml ${}
  EXP_PWD=rig         写了这行            rig            读到 "rig" ✅

❌ 断链（漏写 compose）：
  .env 文件        →  compose 没写  →  容器 env   →  application.yml ${}
  EXP_PWD=rig         ✗ 断                  空              读到 "" ❌
```

---

## 排查方法：逐层验证

以后遇到类似"服务器功能不正常，本地正常"的问题，按这个顺序排查：

```bash
# 1. 查宿主机 .env 有没值
cat /root/.env | grep VAR_NAME

# 2. 查 compose 文件有没搬运
grep VAR_NAME /root/docker-compose-prod.yml

# 3. 查容器里实际有没有收到
docker inspect 容器名 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep VAR_NAME

# 4. 如果以上都有，再看 application.yml 的 key 名是否匹配
```

---

## .env 和 compose 的同步检查清单

每次在 `.env` 里**新加或取消注释**一个变量时，问自己两问：

1. 本地开发：IDE 或本机 env 有没有设？（如果开发环境直接读 env 而非 .env）
2. 服务器部署：docker-compose-prod.yml 的 `environment:` 下有没有对应这行？

只要有一问是"没有"，那条链就是断的。

---

## 速记一句话

**Docker 容器是墙，compose environment 是门。没开门的东西，Java 在里面永远拿不到。**
