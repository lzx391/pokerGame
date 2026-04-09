# 音乐模块关于Redis的使用
音乐服务实现类调用音乐缓存接口，从而连接到音乐缓存实现类  
存->音乐服务实现类调用音乐插入方法，随机把缓存key删掉  
取->音乐服务实现类调用音乐缓存服务接口，把key输入，取出数据，如果没有数据再从Mapper接口即数据库里查出来并更新Redis  
  String json = stringRedisTemplate.opsForValue().get(KEY_MUSIC_LIST);典型从Redis中取值  
  stringRedisTemplate.opsForValue().set(KEY_MUSIC_LIST, objectMapper.writeValueAsString(fresh), musicListTtlSeconds, TimeUnit.SECONDS);典型的往Redis存键值对，并设置过期时间为XXX分钟