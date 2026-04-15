package com.example.mgdemoplus.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 多实例建房：在 {@link #nodeHttpBases} 所列 HTTP 根地址中随机选一执行 {@code createRoom}；
 * 选中非本机时通过 {@code /dpRoom/createRoomRelay} 转发（需 {@link #internalToken}）。
 */
@ConfigurationProperties(prefix = "dp.game.cluster")
public class DpGameClusterProperties {

    private static final Logger log = LoggerFactory.getLogger(DpGameClusterProperties.class);

    private boolean enabled = false;

    /** 与集群内其它实例约定一致，勿暴露到公网 */
    private String internalToken = "dev-cluster-token";

    /**
     * 须包含<strong>所有</strong>游戏实例的 HTTP 根（无尾斜杠），含本机。
     * 使用 {@code node-http-bases[0]=...} 形式配置，避免 Spring 将单行逗号误解析为仅一项。
     */
    private List<String> nodeHttpBases = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public List<String> getNodeHttpBases() {
        return nodeHttpBases;
    }

    public void setNodeHttpBases(List<String> nodeHttpBases) {
        this.nodeHttpBases = nodeHttpBases != null ? nodeHttpBases : new ArrayList<>();
    }

    public List<String> nodeList() {
        if (nodeHttpBases == null || nodeHttpBases.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(nodeHttpBases);
    }

    @PostConstruct
    void logClusterConfig() {
        if (enabled) {
            log.info("dp.game.cluster enabled, node-http-bases count={}, nodes={}", nodeHttpBases.size(), nodeHttpBases);
        } else {
            log.debug("dp.game.cluster disabled (set DP_GAME_CLUSTER_ENABLED=true to randomize createRoom across JVMs)");
        }
    }
}
