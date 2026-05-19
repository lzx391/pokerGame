# 扫描：基础设施 / 配置 / 安全
> 自动生成，供 README 综合使用

## 技术栈

Java 17、Spring Boot 3.5.11、Druid、MyBatis-Plus、Flyway 11.7.2、Redis Lettuce、JJWT。**无 Spring profiles**，仅 `application.yml`。

## 本机 vs Docker 默认（易踩坑）

| 项 | 本机 yml 默认 | Compose 默认 |
|----|---------------|--------------|
| DB 密码 | `123456` | `mgdemo_root` |
| Redis 密码 | `ruoyi123` | `mgdemo_redis` |
| MySQL 端口 | `3306` | 宿主 `3307` |
| Redis 端口 | `6379` | 宿主 `6380` |

本机 `mvn spring-boot:run` 须起对应 MySQL/Redis 或在 `.env` 改 `SPRING_*`。

## 安全

- JWT + Redis **JTI 单会话**（踢下线）
- 过期约 **3 天**（`mgdemoplus.jwt.expiration.time` = 259200000 ms）
- 口令 **bcrypt**（非 MD5）

## Flyway

当前脚本：**V1**（全库基线）、**V2**（好友私信与局内聊天表）、**V3**（房间聊天主键调整）。
