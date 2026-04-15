package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.config.DpGameClusterProperties;
import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.service.serviceImpl.dp.DpRoomServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * 建房调度：集群开启时在配置的节点中随机选一；本机则直接内存建房，否则 HTTP 转发并携带 JWT + 集群口令。
 */
@Service
public class DpRoomClusterPlacementService {

    private static final String RELAY_PATH = "/dpRoom/createRoomRelay";
    private static final String CLUSTER_TOKEN_HEADER = "X-Dp-Cluster-Token";

    private final DpGameClusterProperties clusterProperties;
    private final DpRoomServiceImpl dpRoomService;
    private final RestTemplate restTemplate;

    @Value("${server.port}")
    private int serverPort;

    public DpRoomClusterPlacementService(
            DpGameClusterProperties clusterProperties,
            DpRoomServiceImpl dpRoomService,
            RestTemplate restTemplate) {
        this.clusterProperties = clusterProperties;
        this.dpRoomService = dpRoomService;
        this.restTemplate = restTemplate;
    }

    public DpRoom createRoomDistributed(
            String nickname,
            Integer userId,
            int smallBlindChips,
            int bigBlindChips,
            int startingStackBb,
            String roomPassword,
            String authorizationHeader) {
        if (!clusterProperties.isEnabled()) {
            return dpRoomService.createRoom(nickname, userId, smallBlindChips, bigBlindChips, startingStackBb, roomPassword);
        }
        List<String> bases = new ArrayList<>(clusterProperties.nodeList());
        if (bases.isEmpty()) {
            return dpRoomService.createRoom(nickname, userId, smallBlindChips, bigBlindChips, startingStackBb, roomPassword);
        }
        String selfBase = resolveSelfBase(bases);
        String target = bases.get(ThreadLocalRandom.current().nextInt(bases.size()));
        String selfNorm = normalizeBase(selfBase);
        String targetNorm = normalizeBase(target);
        if (selfNorm.equals(targetNorm)) {
            return dpRoomService.createRoom(nickname, userId, smallBlindChips, bigBlindChips, startingStackBb, roomPassword);
        }
        return forwardCreate(nickname, userId, smallBlindChips, bigBlindChips, startingStackBb, roomPassword, targetNorm, authorizationHeader);
    }

    private DpRoom forwardCreate(
            String nickname,
            Integer userId,
            int smallBlindChips,
            int bigBlindChips,
            int startingStackBb,
            String roomPassword,
            String targetBase,
            String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        headers.set(CLUSTER_TOKEN_HEADER, clusterProperties.getInternalToken());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nickname", nickname);
        if (userId != null) {
            form.add("userId", String.valueOf(userId));
        }
        form.add("smallBlindChips", String.valueOf(smallBlindChips));
        form.add("bigBlindChips", String.valueOf(bigBlindChips));
        form.add("startingStackBb", String.valueOf(startingStackBb));
        if (roomPassword != null && !roomPassword.isEmpty()) {
            form.add("roomPassword", roomPassword);
        }

        String url = targetBase + RELAY_PATH;
        try {
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
            ResponseEntity<DpRoom> resp = restTemplate.postForEntity(url, entity, DpRoom.class);
            DpRoom body = resp.getBody();
            if (body == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "cluster relay empty body");
            }
            return body;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_GATEWAY, "cluster relay failed: " + e.getMessage(), e);
        }
    }

    private String resolveSelfBase(List<String> bases) {
        for (String b : bases) {
            try {
                URI u = URI.create(normalizeBase(b));
                int p = u.getPort();
                if (p < 0) {
                    p = "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
                }
                if (p == serverPort) {
                    return b;
                }
            } catch (Exception ignored) {
                // next
            }
        }
        return "http://127.0.0.1:" + serverPort;
    }

    private static String normalizeBase(String base) {
        if (base == null) {
            return "";
        }
        String s = base.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public DpRoom createRoomRelay(
            String nickname,
            Integer userId,
            int smallBlindChips,
            int bigBlindChips,
            int startingStackBb,
            String roomPassword,
            String clusterToken) {
        if (!clusterProperties.getInternalToken().equals(clusterToken)) {
            throw new ResponseStatusException(FORBIDDEN, "invalid cluster token");
        }
        return dpRoomService.createRoom(nickname, userId, smallBlindChips, bigBlindChips, startingStackBb, roomPassword);
    }
}
