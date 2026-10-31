package com.movie.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Stores working copies only. Publishing always goes through SchedulingWorkspaceService. */
@Service
@RequiredArgsConstructor
public class SchedulingSavedDraftService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public record Summary(String id, Long cinemaId, String cinemaName, LocalDate targetDate, LocalDateTime savedAt) {}
    public record Saved(Summary summary, JsonNode content) {}

    /** 读取本人可访问的排片草稿摘要。 */
    public List<Summary> list(Long userId) {
        Long scope = UserContext.scopeCinemaId(null);
        return jdbc.query("SELECT d.*, c.name AS cinema_name FROM scheduling_saved_draft d "
                        + "LEFT JOIN cinema c ON c.id=d.cinema_id WHERE d.user_id=? ORDER BY d.saved_at DESC",
                (rs, n) -> new Summary(rs.getString("id"), rs.getLong("cinema_id"), rs.getString("cinema_name"),
                        rs.getDate("target_date").toLocalDate(), rs.getTimestamp("saved_at").toLocalDateTime()), userId)
                .stream().filter(row -> scope == null || scope.equals(row.cinemaId())).toList();
    }

    /** 校验本人归属及影院权限后读取草稿内容。 */
    public Saved get(Long userId, String id) {
        var rows = jdbc.query("SELECT d.*, c.name AS cinema_name FROM scheduling_saved_draft d "
                        + "LEFT JOIN cinema c ON c.id=d.cinema_id WHERE d.user_id=? AND d.id=?",
                (rs, n) -> {
                    try {
                        return new Saved(new Summary(rs.getString("id"), rs.getLong("cinema_id"), rs.getString("cinema_name"),
                                rs.getDate("target_date").toLocalDate(), rs.getTimestamp("saved_at").toLocalDateTime()),
                                json.readTree(rs.getString("content")));
                    } catch (java.io.IOException e) { throw new BusinessException("暂存草案数据损坏，无法恢复"); }
                }, userId, id);
        if (rows.isEmpty()) throw new BusinessException(404, "暂存草案不存在或已被更新，请刷新列表");
        UserContext.requireCinema(rows.getFirst().summary().cinemaId());
        return rows.getFirst();
    }

    /** 暂存完整排片草稿及快照，供稍后恢复；不会发布排片。 */
    @Transactional
    public Saved save(Long userId, Long cinemaId, LocalDate targetDate, JsonNode content) {
        UserContext.requireCinema(cinemaId);
        if (targetDate == null || targetDate.isBefore(LocalDate.now()) || targetDate.isAfter(LocalDate.now().plusDays(30))) {
            throw new BusinessException("只能暂存今天起 30 天内的排片草案");
        }
        // Allow unfinished/conflicting schedules, but bound and validate the snapshot envelope.
        if (content == null || !content.isObject() || !content.path("draft").isArray()
                || content.path("draft").size() > 300 || !content.path("workspace").isObject()
                || !content.path("workspace").path("version").isTextual()
                || !content.path("workspace").path("items").isArray()
                || !content.path("settings").isObject() || !content.path("draftCleaning").isInt()
                || content.toString().length() > 512_000) {
            throw new BusinessException("暂存草案格式无效或内容过大");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cinema WHERE id=? AND status=1 AND deleted=0", Integer.class, cinemaId);
        if (count == null || count == 0) throw new BusinessException("影院不可用");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO scheduling_saved_draft(id,user_id,cinema_id,target_date,content,saved_at) "
                        + "VALUES(?,?,?,?,?,CURRENT_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE "
                        + "id=VALUES(id),content=VALUES(content),saved_at=CURRENT_TIMESTAMP(6)",
                id, userId, cinemaId, targetDate, content.toString());
        return get(userId, id);
    }

    /** 校验本人归属与影院权限后删除暂存草稿。 */
    public void delete(Long userId, String id) {
        get(userId, id);
        // ID changes on every save; clearing an applied snapshot cannot erase a newer save in another tab.
        jdbc.update("DELETE FROM scheduling_saved_draft WHERE user_id=? AND id=?", userId, id);
    }
}
