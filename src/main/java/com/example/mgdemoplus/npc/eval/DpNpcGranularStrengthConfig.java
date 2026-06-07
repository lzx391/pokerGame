package com.example.mgdemoplus.npc.eval;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DpNpcGranularStrengthProperties.class)
public class DpNpcGranularStrengthConfig {

    public DpNpcGranularStrengthConfig(DpNpcGranularStrengthProperties properties) {
        DpNpcGranularStrength.bind(properties);
    }
}
