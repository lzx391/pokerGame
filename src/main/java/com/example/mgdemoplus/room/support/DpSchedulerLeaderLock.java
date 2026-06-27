package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.config.DpInstanceProperties;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Single leader for global room heartbeat / NPC timer across app instances.
 */
@Component
public class DpSchedulerLeaderLock {

    private static final Duration LEADER_TTL = Duration.ofSeconds(3);
    private final StringRedisTemplate stringRedisTemplate;
    private final String instanceId;
    private final String token = UUID.randomUUID().toString();
    private volatile boolean leader;

    public DpSchedulerLeaderLock(StringRedisTemplate stringRedisTemplate, DpInstanceProperties instanceProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.instanceId = instanceProperties.getInstanceId();
    }
/**
 * 多机环境设置领导机流程，
 * 首先用leader表示当前是否成功设置过领导，
 * 然后通过token来判断是不是领导，
 * 然后通过setIfAbsent来判断是否需要续期
 * 流程图：
 * 是否有领导->有领导->看是不是自己->是->续期->true
 *                              ->否->false
 *          ->没领导->设置token->true
 * @return
 */
    // public boolean tryBecomeOrRenewLeader() {
    //     String key = DpRoomRedisKeys.HEARTBEAT_LEADER;
    //     //刚开始还没设置leader,跳过if线
    //     if (leader) {
    //         //已经有领导了看是不是自己，是的话续期
    //         String current = stringRedisTemplate.opsForValue().get(key);
    //         if (token.equals(current)) {
    //             stringRedisTemplate.expire(key, LEADER_TTL);
    //             return true;
    //         }
    //         //不是领导设置为false
    //         leader = false;
    //     }
    //     //然后尝试设置token,token赋值之后不可变，如果成功设置了领导机，就把leader设置为true，下次就可以走if线了
    //     //然后其他机子会发现自己的token对不上，只有那个成功设置过的正确的token才能走后续心跳检测流程
    //     Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, token, LEADER_TTL);
    //     if (Boolean.TRUE.equals(ok)) {
    //         leader = true;
    //         return true;
    //     }
    //     //注意setIfAbsent(这里的尝试指的是看有没有记录，如果这组KV已经存在了，就失败了，不进行设置)是指如果缺席就设置，返回true，如果不缺席，说明快过期了，所以需要续期
    //     String current = stringRedisTemplate.opsForValue().get(key);
    //     if (token.equals(current)) {
    //         stringRedisTemplate.expire(key, LEADER_TTL);
    //         leader = true;
    //         return true;
    //     }
    //     return false;
    // }
    public boolean tryBecomeOrRenewLeader() {
        String key = DpRoomRedisKeys.HEARTBEAT_LEADER;
        //刚开始还没设置leader,跳过if线
        if (leader) {
            //已经有领导了看是不是自己，是的话续期，这里有两种可能，一种是获取不到key,key直接过期了，一种是发现不是自己
            String current = stringRedisTemplate.opsForValue().get(key);
            if(current == null) {//这一步是先检验有没有key到底，没有的话说明过期了，leader也需要设置为false
                leader = false;//有leader但是没值，说明这是过期情况，设置完没有leader之后就直接返回了
                return false;
            }
            if (token.equals(current)) {//这一步是看是不是自己，是的话续期
                stringRedisTemplate.expire(key, LEADER_TTL);
                return true;
            }else{
                //如果不是自己的话设置为false
                return false;
            }
        }
    //我觉得没有leader的话肯定是能设置成功
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, token, LEADER_TTL);
        if (Boolean.TRUE.equals(ok)) {
            leader = true;
            return true;
        }
        return false;
    }
    public boolean isLeader() {
        return leader;
    }

    public String instanceId() {
        return instanceId;
    }
}
