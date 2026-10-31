package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssistantOutingQueryTest {
    @Test void allHardFiltersIncludingEndTimeAndExclusionsExecuteTogetherBeforeRanking() throws Exception {
        var config = new MybatisConfiguration();
        try (var input = new ClassPathResource("mapper/ShowtimeMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/ShowtimeMapper.xml", config.getSqlFragments()).parse();
        }
        var start = LocalDateTime.of(2030, 1, 1, 19, 30);
        var filters = new HashMap<String, Object>();
        filters.put("people", 2); filters.put("endBefore", start.toLocalDate().atTime(22, 0));
        filters.put("excludedGenres", List.of("恐怖")); filters.put("excludedMovieIds", List.of(3L));
        filters.put("cinemaKeyword", "南宁"); filters.put("minRating", 8); filters.put("maxDuration", 120);
        var params = new HashMap<String, Object>();
        params.put("movieId", null); params.put("cinemaId", null); params.put("maxPrice", null); params.put("priceExclusive", false);
        params.put("startTime", start); params.put("endTime", start.toLocalDate().plusDays(1).atStartOfDay()); params.put("bookableAfter", start.minusHours(1)); params.put("filters", filters);
        try (var db = DriverManager.getConnection("jdbc:h2:mem:outing-query-" + UUID.randomUUID() + ";MODE=MySQL"); var statement = db.createStatement()) {
            statement.execute("CREATE TABLE movie(id BIGINT, title VARCHAR, poster VARCHAR, duration INT, genre VARCHAR, rating DECIMAL, status INT, deleted INT)");
            statement.execute("CREATE TABLE cinema(id BIGINT, name VARCHAR, address VARCHAR, status INT, deleted INT)");
            statement.execute("CREATE TABLE hall(id BIGINT, name VARCHAR, status INT, deleted INT)");
            statement.execute("CREATE TABLE showtime(id BIGINT, movie_id BIGINT, cinema_id BIGINT, hall_id BIGINT, start_time TIMESTAMP, end_time TIMESTAMP, base_price DECIMAL(10,2), total_seats INT, available_seats INT, status INT, deleted INT, create_time TIMESTAMP, update_time TIMESTAMP)");
            statement.execute("INSERT INTO movie VALUES(1,'喜剧',NULL,100,'喜剧',8,1,0),(2,'惊悚片',NULL,100,'恐怖/惊悚',9,1,0),(3,'已看过',NULL,100,'喜剧',9,1,0)");
            statement.execute("INSERT INTO cinema VALUES(1,'测试影院','南宁',1,0)"); statement.execute("INSERT INTO hall VALUES(1,'1号厅',1,0)");
            statement.execute("INSERT INTO showtime VALUES(11,1,1,1,'2030-01-01 20:20:00','2030-01-01 22:00:00',45,10,2,0,0,NULL,NULL),(12,2,1,1,'2030-01-01 20:00:00','2030-01-01 21:40:00',30,10,3,0,0,NULL,NULL),(13,3,1,1,'2030-01-01 20:00:00','2030-01-01 21:40:00',30,10,3,0,0,NULL,NULL)");
            var bound = config.getMappedStatement("com.movie.ticket.mapper.ShowtimeMapper.selectAssistantCandidates").getBoundSql(params);
            try (var query = db.prepareStatement(bound.getSql())) {
                for (int i = 0; i < bound.getParameterMappings().size(); i++) {
                    String property = bound.getParameterMappings().get(i).getProperty();
                    query.setObject(i + 1, bound.hasAdditionalParameter(property) ? bound.getAdditionalParameter(property) : SystemMetaObject.forObject(params).getValue(property));
                }
                assertEquals(List.of(11L), ids(query));
                statement.execute("UPDATE showtime SET end_time='2030-01-01 22:00:01' WHERE id=11"); assertTrue(ids(query).isEmpty());
                statement.execute("UPDATE showtime SET end_time='2030-01-01 22:00:00',available_seats=1 WHERE id=11"); assertTrue(ids(query).isEmpty());
                statement.execute("UPDATE showtime SET available_seats=2 WHERE id=11"); statement.execute("UPDATE hall SET status=0"); assertTrue(ids(query).isEmpty());
            }
        }
    }
    private List<Long> ids(PreparedStatement query) throws SQLException {
        List<Long> ids = new ArrayList<>(); try (var rows = query.executeQuery()) { while (rows.next()) ids.add(rows.getLong("id")); } return ids;
    }
}
