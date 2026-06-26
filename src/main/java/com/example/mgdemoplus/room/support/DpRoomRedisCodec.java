package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

/**
 * Jackson codec for {@link DpRoomBO} in Redis. Persists fields marked {@code @JsonIgnore} for API
 * (deck, carry-in maps, spectator presence, etc.) while API responses still use the default mapper.
 */
@Component
public final class DpRoomRedisCodec {

    private final ObjectMapper persistMapper;

    public DpRoomRedisCodec() {
        AnnotationIntrospector ignoreJsonIgnore = new JacksonAnnotationIntrospector() {
            @Override
            public boolean hasIgnoreMarker(com.fasterxml.jackson.databind.introspect.AnnotatedMember m) {
                return false;
            }
        };
        this.persistMapper = JsonMapper.builder()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .annotationIntrospector(ignoreJsonIgnore)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    public String toJson(DpRoomBO room) throws JsonProcessingException {
        return persistMapper.writeValueAsString(room);
    }

    public DpRoomBO fromJson(String json) throws JsonProcessingException {
        DpRoomBO room = persistMapper.readValue(json, DpRoomBO.class);
        if (room != null && room.getPlayers() == null) {
            room.setPlayers(new java.util.ArrayList<>());
        }
        return room;
    }

    ObjectMapper persistMapper() {
        return persistMapper;
    }
}
