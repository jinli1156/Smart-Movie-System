package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalBoxOfficeQueryTest {
    @Test void aggregatesAllHistoryExcludesUnpaidRefundedAndKeepsZeroRevenueMovies() throws Exception {
        var configuration = new MybatisConfiguration();
        try (var input = new ClassPathResource("mapper/TicketOrderMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, configuration, "mapper/TicketOrderMapper.xml", configuration.getSqlFragments()).parse();
        }
        String sql = configuration.getMappedStatement("com.movie.ticket.mapper.TicketOrderMapper.selectHistoricalBoxOfficeRanking")
                .getBoundSql(Map.of()).getSql();
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:history-box-office;MODE=MySQL")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE movie(id BIGINT, title VARCHAR(80), poster VARCHAR(100), status INT, deleted INT)");
            statement.execute("CREATE TABLE showtime(id BIGINT, movie_id BIGINT, deleted INT)");
            statement.execute("CREATE TABLE ticket_order(id BIGINT, showtime_id BIGINT, total_price DECIMAL(12,2), seat_count INT, status INT, pay_time TIMESTAMP)");
            statement.execute("INSERT INTO movie VALUES (1,'A',NULL,1,0),(2,'B',NULL,1,0),(3,'C',NULL,1,0),(4,'Upcoming',NULL,2,0),(5,'Deleted',NULL,1,1),(6,'Hidden',NULL,0,0),(7,'Tie',NULL,1,0)");
            statement.execute("INSERT INTO showtime VALUES (1,1,0),(2,2,0),(22,2,1),(5,5,0),(6,6,0),(7,7,0)");
            statement.execute("""
                INSERT INTO ticket_order VALUES
                (1,1,60,1,1,'2020-01-01 10:00:00'),(2,1,40,1,1,'2025-01-01 10:00:00'),
                (3,2,300,3,1,'2024-01-01 10:00:00'),(4,22,200,2,1,'2023-01-01 10:00:00'),
                (5,1,9000,20,3,'2024-01-01 10:00:00'),(6,1,1000,5,0,NULL),(7,1,1000,5,2,NULL),
                (8,5,9999,20,1,'2025-01-01 10:00:00'),(9,6,9999,20,1,'2025-01-01 10:00:00'),
                (10,7,100,5,1,'2021-01-01 10:00:00')
                """);
            List<Long> ids = new ArrayList<>();
            List<BigDecimal> totals = new ArrayList<>();
            try (var rows = statement.executeQuery(sql)) {
                while (rows.next()) {
                    ids.add(rows.getLong("movieId")); totals.add(rows.getBigDecimal("boxOffice"));
                }
            }
            assertEquals(List.of(2L, 7L, 1L, 3L, 4L), ids);
            assertEquals(0, new BigDecimal("500.00").compareTo(totals.getFirst()));
            assertEquals(0, new BigDecimal("100.00").compareTo(totals.get(2)));
            assertEquals(0, BigDecimal.ZERO.compareTo(totals.getLast()));
        }
    }
}
