package com.movie.ticket.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonGenerator;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_OUTPUT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE_TIME_INPUT = new DateTimeFormatterBuilder()
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            .appendOptional(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .toFormatter();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            JavaTimeModule jt = new JavaTimeModule();
            jt.addSerializer(LocalDateTime.class, new StdSerializer<LocalDateTime>(LocalDateTime.class) {
                @Override
                public void serialize(LocalDateTime value, JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider provider) throws IOException {
                    gen.writeString(value.format(DATE_TIME_OUTPUT));
                }
            });
            jt.addDeserializer(LocalDateTime.class, new StdDeserializer<LocalDateTime>(LocalDateTime.class) {
                @Override
                public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    String text = p.getText();
                    if (text == null || text.isBlank()) return null;
                    try {
                        return LocalDateTime.parse(text, DATE_TIME_INPUT);
                    } catch (DateTimeParseException e) {
                        return LocalDateTime.parse(text);
                    }
                }
            });
            jt.addSerializer(LocalDate.class, new StdSerializer<LocalDate>(LocalDate.class) {
                @Override
                public void serialize(LocalDate value, JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider provider) throws IOException {
                    gen.writeString(value.format(DATE_FMT));
                }
            });
            jt.addDeserializer(LocalDate.class, new StdDeserializer<LocalDate>(LocalDate.class) {
                @Override
                public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    String text = p.getText();
                    if (text == null || text.isBlank()) return null;
                    return LocalDate.parse(text, DATE_FMT);
                }
            });

            SimpleModule placeholder = new SimpleModule();
            builder.modulesToInstall(jt, placeholder);
        };
    }
}
