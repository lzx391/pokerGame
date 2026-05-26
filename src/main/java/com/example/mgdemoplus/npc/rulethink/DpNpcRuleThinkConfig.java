package com.example.mgdemoplus.npc.rulethink;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DpNpcRuleThinkProperties.class)
public class DpNpcRuleThinkConfig {

    public DpNpcRuleThinkConfig(DpNpcRuleThinkProperties properties) {
        DpNpcRuleThinkSampler.bind(properties);
    }
}
