package com.stocksim.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    private final ObjectMapper mapper;

    JacksonConfigTest() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().stocksimJacksonCustomizer().customize(builder);
        mapper = builder.build();
    }

    record Sample(BigDecimal amount, LocalDateTime time) {
    }

    @Test
    void serializesAmountAsStringAndTimeWithMillis() throws Exception {
        String json = mapper.writeValueAsString(
                new Sample(new BigDecimal("1005.00"), LocalDateTime.of(2026, 11, 12, 9, 30, 0, 123_000_000)));
        assertThat(json).contains("\"1005.00\"").contains("\"2026-11-12 09:30:00.123\"");
    }

    @Test
    void deserializesAmountFromStringAndTimeWithMillis() throws Exception {
        Sample sample = mapper.readValue(
                "{\"amount\":\"99.50\",\"time\":\"2026-11-12 09:30:00.123\"}", Sample.class);
        assertThat(sample.amount()).isEqualByComparingTo("99.50");
        assertThat(sample.time()).isEqualTo(LocalDateTime.of(2026, 11, 12, 9, 30, 0, 123_000_000));
    }
}