package com.movie.ticket.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real Flyway + MySQL-client checks; never seeds or alters the development database. */
@EnabledIfEnvironmentVariable(named = "LOCAL_INFRA_TEST", matches = "1")
class DemoDatabaseIT {
    private static final String SERVER = "jdbc:mysql://127.0.0.1:3306/";
    private static final String OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private String database, username, password;

    @BeforeEach
    void createDatabase() throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var props = yaml.getObject();
        var env = new StandardEnvironment();
        username = env.resolvePlaceholders(props.getProperty("spring.datasource.username"));
        password = env.resolvePlaceholders(props.getProperty("spring.datasource.password"));
        database = "movie_ticket_demo_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(SERVER + OPTIONS, username, password);
             var s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    @AfterEach
    void removeDatabase() throws Exception {
        if (database == null) return;
        assertTrue(database.matches("movie_ticket_demo_test_[a-f0-9]{32}"));
        try (var c = DriverManager.getConnection(SERVER + OPTIONS, username, password);
             var s = c.createStatement()) {
            s.executeUpdate("DROP DATABASE IF EXISTS `" + database + "`");
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(SERVER + database + OPTIONS, username, password)
                .locations("classpath:db/flyway").target(MigrationVersion.fromVersion(target)).load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(SERVER + database + OPTIONS, username, password);
    }

    private void execute(String... statements) throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            for (String statement : statements) s.execute(statement);
        }
    }

    private long number(String query) throws Exception {
        try (var c = connection(); var s = c.createStatement(); var rows = s.executeQuery(query)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private String runSeed(String at) throws Exception {
        String script = Files.readString(Path.of("src/main/resources/db/data.sql"));
        assertEquals(1, script.split("USE movie_ticket;", -1).length - 1);
        script = script.replace("USE movie_ticket;", "USE `" + database + "`;");
        long instant = LocalDateTime.parse(at).toEpochSecond(ZoneOffset.ofHours(8));
        Path source = Files.createTempFile("demo-sql-source-", ".sql");
        Files.writeString(source, "SET time_zone='-05:00'; SET timestamp=" + instant + ";\n" + script,
                StandardCharsets.UTF_8);
        var process = new ProcessBuilder(System.getenv().getOrDefault("MYSQL_CLI", "mysql"),
                "--host=127.0.0.1", "--port=3306", "--user=" + username,
                "--default-character-set=utf8mb4", "--batch", "--skip-column-names",
                "--execute=SOURCE " + source.toString().replace('\\', '/'));
        process.environment().put("MYSQL_PWD", password);
        Path output = Files.createTempFile("demo-sql-test-", ".log");
        try {
            var child = process.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            child.getOutputStream().close();
            assertTrue(child.waitFor(60, java.util.concurrent.TimeUnit.SECONDS), "mysql client timed out");
            String result = Files.readString(output);
            if (child.exitValue() != 0) throw new IllegalStateException(result);
            return result;
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(source);
        }
    }

    private void assertSchedule(String at, long expectedCount, long expectedDates) throws Exception {
        LocalDateTime start = LocalDateTime.parse(at);
        LocalDate today = start.toLocalDate();
        assertEquals(expectedCount, number("SELECT COUNT(*) FROM showtime"));
        assertEquals(expectedDates, number("SELECT COUNT(DISTINCT DATE(start_time)) FROM showtime"));
        assertEquals(0, number("SELECT COUNT(*) FROM showtime WHERE start_time < '" + start.plusMinutes(15)
                + "' OR start_time >= '" + today.plusDays(11) + "'"));
        assertEquals(20, number("SELECT COUNT(DISTINCT hall_id) FROM showtime WHERE DATE(start_time)='"
                + today.plusDays(10) + "'"));
        assertEquals(0, number("SELECT COUNT(*) FROM showtime s JOIN hall h ON h.id=s.hall_id "
                + "JOIN movie m ON m.id=s.movie_id WHERE s.cinema_id<>h.cinema_id "
                + "OR s.total_seats<>h.total_seats OR s.available_seats<>h.total_seats "
                + "OR TIMESTAMPDIFF(MINUTE,s.start_time,s.end_time)<>m.duration "
                + "OR m.status<>1 OR m.release_date>DATE(s.start_time)"));
        assertEquals(0, number("SELECT COUNT(*) FROM showtime a JOIN showtime b ON a.id<b.id "
                + "AND a.hall_id=b.hall_id AND a.start_time<DATE_ADD(b.end_time,INTERVAL 20 MINUTE) "
                + "AND b.start_time<DATE_ADD(a.end_time,INTERVAL 20 MINUTE)"));
        assertEquals(1, number("SELECT COUNT(*) FROM user WHERE username='cinema_admin' AND cinema_id=1"));
        assertEquals(1, number("SELECT COUNT(*) FROM coupon_code WHERE code='JINLI699' "
                + "AND valid_from<='" + start + "' AND expire_time>'" + start + "' AND redeemed_count=0"));
        assertEquals(3, number("SELECT COUNT(*) FROM user"));
        assertEquals(40, number("SELECT COUNT(*) FROM movie"));
    }

    @ParameterizedTest
    @CsvSource({"2026-10-26T08:00:00,880,11", "2026-10-26T10:00:00,860,11",
            "2026-10-26T23:55:00,800,10", "2035-12-31T08:00:00,880,11"})
    void freshInstallSeedsRelativeChinaDates(String at, long count, long dates) throws Exception {
        flyway("24").migrate();
        runSeed(at);
        assertSchedule(at, count, dates);
        // Seed hashes must remain usable by Spring Security for all three accounts.
        try (var c = connection(); var s = c.createStatement(); var rows = s.executeQuery("SELECT password FROM user")) {
            var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
            while (rows.next()) assertTrue(encoder.matches("admin123", rows.getString(1)));
        }
    }

    @Test
    void repeatedResetRemovesNewFeaturesAndCircularCouponReferences() throws Exception {
        flyway("24").migrate();
        runSeed("2026-10-26T08:00:00");
        execute("INSERT INTO user(id,username,password) VALUES(99,'old-demo-user','unused')",
                "INSERT INTO ticket_order(id,order_no,user_id,showtime_id,seats,seat_count,total_price,original_price) "
                        + "SELECT 99,'OLD-DEMO',99,MIN(id),'1排1座',1,40,40 FROM showtime",
                "INSERT INTO user_coupon(id,user_id,name,amount,order_id,valid_from,expire_time) "
                        + "VALUES(99,99,'old coupon',1,99,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY))",
                "UPDATE ticket_order SET coupon_id=99 WHERE id=99",
                "INSERT INTO coupon_code_redemption(coupon_code_id,user_id,user_coupon_id,redeem_time) VALUES(1,99,99,NOW())",
                "UPDATE coupon_code SET redeemed_count=1",
                "INSERT INTO assistant_conversation(id,user_id,title) VALUES('old-conversation',99,'old')",
                "INSERT INTO assistant_turn(conversation_id,request_id,question,answer,tool_results,suggestions) "
                        + "VALUES('old-conversation','old-request','old','old','[]','[]')",
                "INSERT INTO assistant_preference(user_id,preferences) VALUES(99,'{}')",
                "INSERT INTO assistant_checkout(id,user_id,showtime_id,criteria,snapshot,expires_at) "
                        + "SELECT 'old-checkout',99,MIN(id),'{}','{}',DATE_ADD(NOW(),INTERVAL 1 DAY) FROM showtime",
                "INSERT INTO scheduling_saved_draft(id,user_id,cinema_id,target_date,content) VALUES('old-draft',99,1,CURDATE(),'{}')");
        runSeed("2026-10-27T08:00:00");
        assertSchedule("2026-10-27T08:00:00", 880, 11);
        for (String table : new String[]{"assistant_conversation", "assistant_turn", "assistant_preference",
                "assistant_checkout", "scheduling_saved_draft", "coupon_code_redemption", "ticket_order"}) {
            assertEquals(0, number("SELECT COUNT(*) FROM " + table), table);
        }
        assertEquals(1, number("SELECT COUNT(*) FROM user_coupon WHERE user_id=1 AND order_id IS NULL"));
    }

    @Test
    void seedFailureRollsBackAllBusinessChangesAndReleasesLock() throws Exception {
        flyway("24").migrate();
        runSeed("2026-10-26T08:00:00");
        execute("UPDATE user SET nickname='rollback sentinel' WHERE id=1",
                "CREATE TRIGGER reject_demo_movie BEFORE INSERT ON movie FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected seed failure'");
        assertTrue(assertThrows(IllegalStateException.class, () -> runSeed("2026-10-27T08:00:00"))
                .getMessage().contains("injected seed failure"));
        assertSchedule("2026-10-26T08:00:00", 880, 11);
        assertEquals(1, number("SELECT COUNT(*) FROM user WHERE id=1 AND nickname='rollback sentinel'"));
        assertEquals(1, number("SELECT IS_FREE_LOCK(CONCAT('movie_demo:',MD5(DATABASE())))"));
    }

    @Test
    void outdatedSchemaFailsBeforeDeletingAnything() throws Exception {
        flyway("23").migrate();
        execute("INSERT INTO user(username,password) VALUES('keep-me','unused')");
        assertTrue(assertThrows(IllegalStateException.class, () -> runSeed("2026-10-26T08:00:00"))
                .getMessage().contains("Flyway V24"));
        assertEquals(1, number("SELECT COUNT(*) FROM user WHERE username='keep-me'"));
    }

    @Test
    void legacyUpgradeKeepsDataAndAddsMissingConstraints() throws Exception {
        flyway("23").migrate();
        execute("ALTER TABLE ticket_order DROP INDEX uk_ticket_code, ADD INDEX idx_ticket_code(ticket_code)",
                "ALTER TABLE user DROP INDEX idx_role_status, MODIFY status INT NULL DEFAULT 1",
                "INSERT INTO user(username,password) VALUES('keep-me','unused')");
        flyway("24").migrate();
        assertEquals(1, number("SELECT COUNT(*) FROM user WHERE username='keep-me'"));
        assertEquals(1, number("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='ticket_order' AND INDEX_NAME='uk_ticket_code' AND NON_UNIQUE=0"));
        assertEquals(0, number("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='user' AND COLUMN_NAME='status' AND IS_NULLABLE='YES'"));
        flyway("24").validate();
    }

    @Test
    void legacyNullDataStopsBeforeAnySchemaChange() throws Exception {
        flyway("23").migrate();
        execute("ALTER TABLE user DROP INDEX idx_role_status, MODIFY status INT NULL DEFAULT 1",
                "INSERT INTO user(username,password,status) VALUES('keep-null','unused',NULL)");
        assertTrue(assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway("24").migrate())
                .getMessage().contains("NULL roles"));
        assertEquals(1, number("SELECT COUNT(*) FROM user WHERE username='keep-null' AND status IS NULL"));
        assertEquals(0, number("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='user' AND INDEX_NAME='idx_role_status'"));
    }

    @Test
    void legacyDuplicateTicketCodesStopBeforeAnySchemaChange() throws Exception {
        flyway("23").migrate();
        execute("ALTER TABLE ticket_order DROP INDEX uk_ticket_code, ADD INDEX idx_ticket_code(ticket_code)",
                "ALTER TABLE user DROP INDEX idx_role_status",
                "INSERT INTO user(id,username,password) VALUES(1,'keep-me','unused')",
                "INSERT INTO cinema(id,name,address) VALUES(1,'test cinema','test address')",
                "INSERT INTO hall(id,cinema_id,name,total_rows,total_cols,total_seats) VALUES(1,1,'test hall',10,10,100)",
                "INSERT INTO movie(id,title,duration) VALUES(1,'test movie',90)",
                "INSERT INTO showtime(id,movie_id,cinema_id,hall_id,start_time,end_time,base_price,total_seats,available_seats) "
                        + "VALUES(1,1,1,1,NOW(),DATE_ADD(NOW(),INTERVAL 90 MINUTE),40,100,100)",
                "INSERT INTO ticket_order(order_no,user_id,showtime_id,seats,seat_count,total_price,original_price,ticket_code) "
                        + "VALUES('legacy-1',1,1,'1排1座',1,40,40,'12345678'),('legacy-2',1,1,'1排2座',1,40,40,'12345678')");
        assertTrue(assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway("24").migrate())
                .getMessage().contains("duplicate username, order number or ticket code"));
        assertEquals(2, number("SELECT COUNT(*) FROM ticket_order WHERE ticket_code='12345678'"));
        assertEquals(0, number("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='user' AND INDEX_NAME='idx_role_status'"));
    }
}
