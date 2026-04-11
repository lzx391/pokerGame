# 音乐模块关于 Redis 的使用

**Docker**：`docker compose` 已包含 Redis 服务，应用容器内连 `redis:6379`；宿主机调试见 [DOCKER.md](DOCKER.md)（默认 `localhost:6380`、口令与 compose 中 `REDIS_PASSWORD` 一致）。

音乐服务实现类调用音乐缓存接口，从而连接到音乐缓存实现类  
存->音乐服务实现类调用音乐插入方法，随机把缓存key删掉  
取->音乐服务实现类调用音乐缓存服务接口，把key输入，取出数据，如果没有数据再从Mapper接口即数据库里查出来并更新Redis  
  String json = stringRedisTemplate.opsForValue().get(KEY_MUSIC_LIST);典型从Redis中取值  
  stringRedisTemplate.opsForValue().set(KEY_MUSIC_LIST, objectMapper.writeValueAsString(fresh), musicListTtlSeconds, TimeUnit.SECONDS);典型的往Redis存键值对，并设置过期时间为XXX分钟
  ---
  # jwt+redis结合的功能
  思路是这样的，当用户登录的时候，利用用户昵称和随机生成的UUID作为jti一起作为载荷生成token  
  并且把用户昵称作为键，jti作为值写入redis，时间和jwt的有效期一致  
  当用户下次发请求的时候，过滤器将token解析出来，通过昵称从redis取出cache_jti,这时候验证cache_jti是否存在是否和当前的jti一样，因为不同时刻登陆的uuid是不一样的，所以如果新的端登录之后，redis信息被覆盖，旧的端就会因为cache_jti和自己token里的jti不一样而被登出  
  后续如果要踢人，可以删redis的键值对