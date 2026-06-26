package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.room.support.DpDevInstanceRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Dev-only helpers for multi-instance health / discovery (optional tooling).
 */
@RestController
@RequestMapping("/dp/dev")
public class DpDevController {

    @Value("${server.port:8088}")
    private int serverPort;

    @Autowired(required = false)
    private DpDevInstanceRegistry devInstanceRegistry;

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "port", serverPort
        );
    }

    @GetMapping("/instances")
    public List<Integer> instances() {
        if (devInstanceRegistry != null) {
            List<Integer> live = devInstanceRegistry.listLiveInstancePorts();
            if (!live.isEmpty()) {
                return live;
            }
        }
        return Collections.singletonList(serverPort);
    }
}
