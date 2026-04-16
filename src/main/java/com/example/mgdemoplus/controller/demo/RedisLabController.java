package com.example.mgdemoplus.controller.demo;

import com.example.mgdemoplus.service.demo.RedisLabService;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis 实验接口（仅本地学习）：启动应用后可直接在浏览器或 curl 调用，无需 JWT。
 * 键名会自动加上前缀 {@link RedisLabService#KEY_PREFIX}。
 */
@RestController
@RequestMapping("/demo/redis")
public class RedisLabController {

    private final RedisLabService redisLabService;

    public RedisLabController(RedisLabService redisLabService) {
        this.redisLabService = redisLabService;
    }

    /** 连通性：写入时间戳再读出。 */
    @GetMapping("/ping")
    public ResultUtil ping() {
        return ResultUtil.ok().data("lab", redisLabService.ping());
    }

    /**
     * 存字符串。可选 ttlSeconds：正数则带过期秒数。
     * 例：{@code /demo/redis/set?key=mytest&value=hello&ttlSeconds=60}
     */
    @GetMapping("/set")
    public ResultUtil set(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam(required = false) Integer ttlSeconds) {
        try {
            return ResultUtil.ok().data("lab", redisLabService.setKv(key, value, ttlSeconds));
        } catch (IllegalArgumentException e) {
            return ResultUtil.error().data("message", e.getMessage());
        }
    }

    /** 例：{@code /demo/redis/get?key=mytest} */
    @GetMapping("/get")
    public ResultUtil get(@RequestParam String key) {
        try {
            return ResultUtil.ok().data("lab", redisLabService.getKv(key));
        } catch (IllegalArgumentException e) {
            return ResultUtil.error().data("message", e.getMessage());
        }
    }

    /**
     * 计数器自增，默认键名 {@code visit}、步长 {@code 1}。
     * 仅打开 {@code /demo/redis/incr} 不带参数也可；自定义：{@code /demo/redis/incr?key=visit&delta=1}
     */
    @GetMapping("/incr")
    public ResultUtil incr(
            @RequestParam(defaultValue = "visit") String key,
            @RequestParam(defaultValue = "1") long delta) {
        try {
            return ResultUtil.ok().data("lab", redisLabService.increment(key, delta));
        } catch (IllegalArgumentException e) {
            return ResultUtil.error().data("message", e.getMessage());
        }
    }

    /** 仅当 key 不存在时写入（抢锁思路的雏形）。 */
    @GetMapping("/setnx")
    public ResultUtil setIfAbsent(@RequestParam String key, @RequestParam String value) {
        try {
            return ResultUtil.ok().data("lab", redisLabService.setIfAbsent(key, value));
        } catch (IllegalArgumentException e) {
            return ResultUtil.error().data("message", e.getMessage());
        }
    }

    /** 删除本实验前缀下的键（浏览器地址栏可打开）。 */
    @GetMapping("/del")
    public ResultUtil del(@RequestParam String key) {
        return doDelete(key);
    }

    /** 与 {@link #del} 相同，供 curl {@code -X DELETE} 使用。 */
    @DeleteMapping("/delete")
    public ResultUtil delete(@RequestParam String key) {
        return doDelete(key);
    }

    private ResultUtil doDelete(String key) {
        try {
            return ResultUtil.ok().data("lab", redisLabService.delete(key));
        } catch (IllegalArgumentException e) {
            return ResultUtil.error().data("message", e.getMessage());
        }
    }
}
