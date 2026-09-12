package com.uprera.estates.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class JacksonConfig {

    /**
     * Serialize ObjectId as its 24-char hex string.
     *
     * Without this, Jackson reflects over ObjectId and emits
     * {@code {"timestamp":..., "date":...}}. The previous Node/Mongoose API
     * emitted a plain hex string, and the frontend builds document URLs from
     * these values — so this is required for byte-level parity with the API
     * being replaced, not merely a cosmetic preference.
     */
    @Bean
    public SimpleModule bsonModule() {
        SimpleModule module = new SimpleModule("bson");
        module.addSerializer(ObjectId.class, new JsonSerializer<>() {
            @Override
            public void serialize(ObjectId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toHexString());
            }
        });
        return module;
    }
}
