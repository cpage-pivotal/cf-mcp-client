package org.tanzu.mcpclient.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Jackson configuration for custom timestamp handling.
 * Configures ObjectMapper to handle timestamps with microsecond precision
 * and without timezone indicators (assumes UTC).
 */
@Configuration
public class JacksonConfiguration {

    /**
     * Custom Instant deserializer that handles flexible timestamp formats:
     * - Standard ISO-8601 with timezone: 2025-11-21T16:14:24.060633Z
     * - Microseconds without timezone: 2025-11-21T16:14:24.060633 (assumes UTC)
     * - Various fractional second precisions (milliseconds, microseconds, nanoseconds)
     */
    public static class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

        // Formatter that handles local datetime with optional timezone
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .optionalStart()
                .appendOffsetId()
                .optionalEnd()
                .toFormatter();

        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String timestamp = p.getText();

            if (timestamp == null || timestamp.isEmpty()) {
                return null;
            }

            try {
                // Try standard Instant.parse first (handles timestamps with Z or offset)
                return Instant.parse(timestamp);
            } catch (Exception e1) {
                try {
                    // If that fails, try parsing as LocalDateTime and assume UTC
                    LocalDateTime ldt = LocalDateTime.parse(timestamp, FORMATTER);
                    return ldt.toInstant(ZoneOffset.UTC);
                } catch (Exception e2) {
                    // If both fail, append 'Z' and try again (for timestamps without timezone)
                    try {
                        return Instant.parse(timestamp + "Z");
                    } catch (Exception e3) {
                        throw new IOException("Cannot parse timestamp: " + timestamp, e3);
                    }
                }
            }
        }
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register JavaTimeModule for Java 8 date/time support
        mapper.registerModule(new JavaTimeModule());

        // Add custom module with flexible Instant deserializer
        SimpleModule customModule = new SimpleModule();
        customModule.addDeserializer(Instant.class, new FlexibleInstantDeserializer());
        mapper.registerModule(customModule);

        // Configure mapper to be lenient with timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }
}

