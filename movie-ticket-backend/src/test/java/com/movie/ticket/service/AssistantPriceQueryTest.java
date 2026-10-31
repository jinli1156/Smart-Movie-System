package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class AssistantPriceQueryTest {
    @Test void strictBudgetExcludesFortyAndSoldOutBeforePagination() throws Exception {
        String sql = sql(new BigDecimal("40"), true);
        assertTrue(sql.contains("s.base_price < ?"));
        assertFalse(sql.contains("s.base_price <= ?"));
        assertTrue(sql.contains("s.available_seats > 0"));
        assertTrue(sql.contains("ORDER BY s.base_price ASC, s.start_time ASC"));
    }

    @Test void inclusiveBudgetAllowsBoundaryAndNoBudgetKeepsChronologicalOrder() throws Exception {
        assertTrue(sql(new BigDecimal("40"), false).contains("s.base_price <= ?"));
        String noBudget = sql(null, false);
        assertFalse(noBudget.contains("s.base_price <"));
        assertTrue(noBudget.contains("ORDER BY s.start_time ASC"));
    }

    private String sql(BigDecimal maxPrice, boolean exclusive) throws Exception {
        return sql(maxPrice, exclusive, java.util.Map.of());
    }

    @Test void allHardConditionsAreBoundInSqlBeforePagination() throws Exception {
        String sql = sql(new BigDecimal("50"), false, java.util.Map.of("genre", "科幻", "minRating", 8,
                "maxDuration", 120, "hallKeyword", "IMAX", "cinemaKeyword", "南宁", "people", 2));
        for (String predicate : java.util.List.of("m.rating >= ?", "m.duration <= ?", "s.available_seats >= ?",
                "LOCATE(?, m.genre)", "LOWER(h.name)", "c.address", "h.status = 1", "s.start_time >= ?", "s.start_time < ?")) {
            assertTrue(sql.contains(predicate), predicate);
        }
        assertFalse(sql.contains("科幻")); // User text must remain a bound parameter.
        assertFalse(sql.contains("IMAX"));
    }

    private String sql(BigDecimal maxPrice, boolean exclusive, java.util.Map<String, Object> filters) throws Exception {
        var configuration = new MybatisConfiguration();
        try (var input = new ClassPathResource("mapper/ShowtimeMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, configuration, "mapper/ShowtimeMapper.xml", configuration.getSqlFragments()).parse();
        }
        var parameters = new HashMap<String, Object>();
        parameters.put("maxPrice", maxPrice); parameters.put("priceExclusive", exclusive);
        parameters.put("filters", filters);
        parameters.put("movieId", null); parameters.put("cinemaId", null);
        parameters.put("startTime", LocalDateTime.now()); parameters.put("endTime", LocalDateTime.now().plusDays(1));
        parameters.put("bookableAfter", LocalDateTime.now());
        return configuration.getMappedStatement("com.movie.ticket.mapper.ShowtimeMapper.selectAssistantPage")
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }
}
