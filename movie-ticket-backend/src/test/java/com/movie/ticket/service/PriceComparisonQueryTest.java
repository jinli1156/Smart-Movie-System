package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Execute the actual mapper SQL, including joined availability filters, against an isolated database. */
class PriceComparisonQueryTest {
    @Test void excludesSoldOutClosedDeletedAndOutsideDateOrCutoff() throws Exception {
        var config = new MybatisConfiguration();
        try (var input = new ClassPathResource("mapper/ShowtimeMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/ShowtimeMapper.xml", config.getSqlFragments()).parse();
        }
        var start = LocalDateTime.of(2030, 1, 1, 0, 0);
        Map<String, Object> params = Map.of("movieId", 1L, "people", 2, "startTime", start,
                "endTime", start.plusDays(1), "bookableAfter", start.plusHours(10).plusMinutes(15));
        var bound = config.getMappedStatement("com.movie.ticket.mapper.ShowtimeMapper.selectPriceComparisonViews").getBoundSql(params);
        try (var db = DriverManager.getConnection("jdbc:h2:mem:price-query-" + UUID.randomUUID() + ";MODE=MySQL"); var stmt = db.createStatement()) {
            stmt.execute("CREATE TABLE movie(id BIGINT, title VARCHAR, poster VARCHAR, duration INT, status INT, deleted INT)");
            stmt.execute("CREATE TABLE cinema(id BIGINT, name VARCHAR, status INT, deleted INT)");
            stmt.execute("CREATE TABLE hall(id BIGINT, name VARCHAR, status INT, deleted INT)");
            stmt.execute("CREATE TABLE showtime(id BIGINT, movie_id BIGINT, cinema_id BIGINT, hall_id BIGINT, start_time TIMESTAMP, end_time TIMESTAMP, base_price DECIMAL(10,2), total_seats INT, available_seats INT, status INT, deleted INT, create_time TIMESTAMP, update_time TIMESTAMP)");
            stmt.execute("INSERT INTO movie VALUES(1,'电影',NULL,100,1,0)");
            stmt.execute("INSERT INTO cinema VALUES(1,'影院',1,0)");
            stmt.execute("INSERT INTO hall VALUES(1,'影厅',1,0)");
            stmt.execute("INSERT INTO showtime VALUES(1,1,1,1,'2030-01-01 10:15:00','2030-01-01 12:00:00',41,10,2,0,0,NULL,NULL)");
            try (var query = db.prepareStatement(bound.getSql())) {
                for (int i = 0; i < bound.getParameterMappings().size(); i++) query.setObject(i + 1, params.get(bound.getParameterMappings().get(i).getProperty()));
                assertEquals(List.of(1L), ids(query)); // Exact 15-minute cutoff and exactly enough seats are eligible.
                for (String update : List.of("available_seats=1", "status=2", "deleted=1", "start_time='2030-01-01 10:14:59'", "start_time='2030-01-02 00:00:00'")) {
                    stmt.execute("UPDATE showtime SET " + update);
                    assertTrue(ids(query).isEmpty(), update);
                    stmt.execute("UPDATE showtime SET available_seats=2,status=0,deleted=0,start_time='2030-01-01 10:15:00'");
                }
                for (String table : List.of("movie", "cinema", "hall")) {
                    stmt.execute("UPDATE " + table + " SET status=0");
                    assertTrue(ids(query).isEmpty(), table + " closed");
                    stmt.execute("UPDATE " + table + " SET status=1,deleted=1");
                    assertTrue(ids(query).isEmpty(), table + " deleted");
                    stmt.execute("UPDATE " + table + " SET deleted=0");
                }
                assertEquals(List.of(1L), ids(query));
            }
        }
    }
    private List<Long> ids(PreparedStatement query) throws SQLException {
        var ids = new ArrayList<Long>();
        try (var rows = query.executeQuery()) { while (rows.next()) ids.add(rows.getLong("id")); }
        return ids;
    }
}
