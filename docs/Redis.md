# Redis 用法备忘（曲库缓存与扩展思路）

**本篇说明**：读者为后端维护人员；本篇记录 **Redis 在音乐列表缓存里的实际写法**，文末两段为「JWT + Redis 踢下线」「大厅分页缓存」的**想法备忘**（是否与当前主线实现一致请以代码为准）。

**Docker**：`docker compose` 已包含 Redis 服务，应用容器内连 `redis:6379`；宿主机调试见 [DOCKER.md](DOCKER.md)（默认 `localhost:6380`、口令与 compose 中 `REDIS_PASSWORD` 一致）。

音乐服务实现类调用音乐缓存接口，从而连接到音乐缓存实现类。  
存：音乐服务实现类调用音乐插入方法，随机把缓存 key 删掉。  
取：音乐服务实现类调用音乐缓存服务接口，把 key 输入取出数据；如果没有数据再从 Mapper（数据库）里查出来并更新 Redis。

```java
String json = stringRedisTemplate.opsForValue().get(KEY_MUSIC_LIST); // 典型从 Redis 取值
stringRedisTemplate.opsForValue().set(KEY_MUSIC_LIST, objectMapper.writeValueAsString(fresh), musicListTtlSeconds, TimeUnit.SECONDS); // 写入并设 TTL
```

---

## JWT + Redis 组合（多端登录 / 踢下线思路）

思路：用户登录时用用户昵称与随机生成的 `jti` 一起写入 JWT 载荷；把用户昵称作为键、`jti` 作为值写入 Redis，TTL 与 JWT 有效期一致。下次请求时用过滤器解析 token，凭昵称取出 Redis 中的 `cache_jti`，比对是否与当前 token 里的 `jti` 一致；新端登录会覆盖 Redis，旧端因不一致可被登出。若要踢人，可删除对应 Redis 键。

---

## 房间列表分页 + Redis（设计思路备忘）

一类是**无条件默认分页**：键可设计为「页码 + 页大小」映射到房间列表 VO 的 JSON。  
另一类是**带条件查询**：将查询条件与分页信息一起做哈希加工作为键，值为对应 VO 的 JSON；请求时先算哈希再查 Redis，未命中则查库并回写。
