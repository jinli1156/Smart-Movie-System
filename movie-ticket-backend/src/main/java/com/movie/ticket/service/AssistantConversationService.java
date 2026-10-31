package com.movie.ticket.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.movie.ticket.dto.AssistantChatRequest;
import com.movie.ticket.dto.AssistantMessage;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.vo.AssistantChatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/** Persists server-generated turns; browser history is never trusted for a saved conversation. */
@Service
@RequiredArgsConstructor
public class AssistantConversationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public record Prepared(String conversationId, String requestId, Long userId,
                           AssistantChatRequest request, AssistantChatVO cached) {}
    public record Conversation(String id, String title, LocalDateTime updatedAt) {}
    public record Turn(long id, String question, String answer, String source, String status,
                       List<Map<String, Object>> toolResults, List<String> suggestions, LocalDateTime createdAt, JsonNode watchPlan) {}

    private void authenticated(Long userId) {
        if (userId == null) throw new BusinessException(401, "请先登录后查看或保存对话");
    }

    private Conversation own(String id, Long userId, boolean lock) {
        authenticated(userId);
        var rows = jdbc.query("SELECT id,title,update_time FROM assistant_conversation WHERE id=? AND user_id=?"
                        + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new Conversation(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()), id, userId);
        if (rows.isEmpty()) throw new BusinessException(404, "对话不存在或已删除");
        return rows.getFirst();
    }

    /** 分页查询本人的助手会话摘要。 */
    public Map<String, Object> list(Long userId, int page) {
        authenticated(userId);
        if (page < 1 || page > 10000) throw new BusinessException(400, "页码无效");
        var rows = jdbc.query("SELECT id,title,update_time FROM assistant_conversation WHERE user_id=? ORDER BY update_time DESC,id DESC LIMIT 21 OFFSET ?",
                (rs, n) -> new Conversation(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()), userId, (page - 1) * 20);
        return Map.of("items", rows.stream().limit(20).toList(), "hasMore", rows.size() > 20, "page", page);
    }

    /** 读取本人会话及消息，并支持向前加载历史轮次。 */
    public Map<String, Object> detail(String id, Long userId, Long before) {
        var conversation = own(id, userId, false);
        if (before != null && before < 1) throw new BusinessException(400, "消息位置无效");
        var turns = jdbc.query("SELECT t.* FROM assistant_turn t JOIN assistant_conversation c ON c.id=t.conversation_id "
                        + "WHERE c.id=? AND c.user_id=? AND t.id<? ORDER BY t.id DESC LIMIT 31",
                this::turn, id, userId, before == null ? Long.MAX_VALUE : before);
        boolean more = turns.size() > 30;
        var page = new ArrayList<>(turns.stream().limit(30).toList());
        Collections.reverse(page);
        return Map.of("conversation", conversation, "turns", page, "hasMore", more, "watchPlan", readPlan(id));
    }

    /** 校验会话归属后删除本人的会话记录。 */
    @Transactional
    public void delete(String id, Long userId) {
        own(id, userId, true);
        jdbc.update("DELETE FROM assistant_conversation WHERE id=? AND user_id=?", id, userId);
    }

    /** 为当前问题准备会话、历史消息和观影条件，处理客户端重复消息。 */
    @Transactional
    public Prepared prepare(AssistantChatRequest request, Long userId) {
        if (userId == null) {
            if (request.getConversationId() != null) authenticated(null);
            try {
                var plan = new AssistantWatchPlan(json, request.getGuestPlan() == null ? null : json.readTree(request.getGuestPlan()), null);
                if (request.getPlanPatch() != null) plan.patch(request.getPlanPatch());
                request.setWatchPlan(plan.snapshot());
            } catch (Exception ex) { throw new BusinessException(400, "观影条件无效，请新建对话后重试"); }
            return new Prepared(null, null, null, request, null);
        }
        String id = request.getConversationId();
        if (id == null) {
            id = UUID.randomUUID().toString();
            String title = request.getMessage().strip().replaceAll("\\s+", " ");
            jdbc.update("INSERT INTO assistant_conversation(id,user_id,title) VALUES(?,?,?)",
                    id, userId, title.substring(0, Math.min(60, title.length())));
        }
        own(id, userId, true);
        String requestId = request.getClientMessageId() == null ? UUID.randomUUID().toString() : request.getClientMessageId();
        var duplicates = jdbc.query("SELECT * FROM assistant_turn WHERE conversation_id=? AND request_id=?", this::turn, id, requestId);
        if (!duplicates.isEmpty()) {
            var turn = duplicates.getFirst();
            if (!turn.question().equals(request.getMessage())) throw new BusinessException(409, "消息标识已使用，请重新发送");
            if (turn.status().equals("pending")) throw new BusinessException(409, "该消息仍在处理，请稍后查看历史");
            var cached = new AssistantChatVO(turn.answer(), turn.suggestions(), turn.source(), turn.toolResults());
            cached.setWatchPlan(turn.watchPlan());
            return new Prepared(id, requestId, userId, request, cached);
        }
        int claimed = jdbc.update("UPDATE assistant_conversation SET active_request_id=?,busy_until=?,update_time=NOW() "
                + "WHERE id=? AND user_id=? AND (busy_until IS NULL OR busy_until<NOW())", requestId, LocalDateTime.now().plusMinutes(4), id, userId);
        if (claimed != 1) throw new BusinessException(409, "此对话正在回答，请稍后再发送");
        // A crashed worker's lease eventually expires; never treat its unfinished answer as context.
        jdbc.update("UPDATE assistant_turn SET status='complete',source='unavailable',answer='上次回答中断，请重新提问。',update_time=NOW() "
                + "WHERE conversation_id=? AND status='pending'", id);
        var previous = jdbc.query("SELECT * FROM assistant_turn WHERE conversation_id=? AND status='complete' AND source='model' ORDER BY id DESC LIMIT 5", this::turn, id);
        Collections.reverse(previous);
        List<AssistantMessage> history = new ArrayList<>();
        for (var turn : previous) {
            history.add(message("user", turn.question())); history.add(message("assistant", turn.answer()));
        }
        var effective = new AssistantChatRequest(); effective.setMessage(request.getMessage()); effective.setHistory(history);
        effective.setConversationId(id); effective.setClientMessageId(requestId);
        effective.setWatchPlan(readPlan(id)); effective.setSavedPreferences(preferences(userId));
        if (request.getPlanPatch() != null) {
            try {
                var plan = new AssistantWatchPlan(json, effective.getWatchPlan(), effective.getSavedPreferences());
                effective.setWatchPlan(plan.patch(request.getPlanPatch()));
                effective.setPlanPatch(request.getPlanPatch());
            } catch (IllegalArgumentException ex) { throw new BusinessException(400, "观影条件修改无效，请刷新会话后重试"); }
        }
        jdbc.update("INSERT INTO assistant_turn(conversation_id,request_id,question,answer,tool_results,suggestions) VALUES(?,?,?,'','[]','[]')",
                id, requestId, request.getMessage());
        return new Prepared(id, requestId, userId, effective, null);
    }

    /** 结束当前对话轮次，保存最终回答、工具结果及执行状态。 */
    @Transactional
    public void finish(Prepared prepared, AssistantChatVO response) {
        if (prepared.conversationId() == null || prepared.cached() != null) return;
        // Deletion during generation must not recreate a conversation or its messages.
        int changed = jdbc.update("UPDATE assistant_turn SET answer=?,source=?,status='complete',tool_results=?,suggestions=?,watch_plan=?,update_time=NOW() "
                        + "WHERE conversation_id=? AND request_id=? AND status='pending' "
                        + "AND EXISTS (SELECT 1 FROM assistant_conversation c WHERE c.id=assistant_turn.conversation_id AND c.user_id=?)",
                response.getAnswer(), response.getSource(), encode(response.getToolResults()), encode(response.getSuggestions()),
                response.getWatchPlan() == null ? null : encode(response.getWatchPlan()),
                prepared.conversationId(), prepared.requestId(), prepared.userId());
        if (changed == 1 && "model".equals(response.getSource()) && response.getWatchPlan() != null) {
            jdbc.update("UPDATE assistant_conversation SET watch_plan=? WHERE id=? AND user_id=? AND active_request_id=?",
                    encode(response.getWatchPlan()), prepared.conversationId(), prepared.userId(), prepared.requestId());
        }
        jdbc.update("UPDATE assistant_conversation SET active_request_id=NULL,busy_until=NULL,update_time=NOW() WHERE id=? AND user_id=? AND active_request_id=?",
                prepared.conversationId(), prepared.userId(), prepared.requestId());
    }

    private AssistantMessage message(String role, String text) {
        var message = new AssistantMessage(); message.setRole(role); message.setContent(text.substring(0, Math.min(4000, text.length()))); return message;
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException("无法保存对话"); }
    }
    private JsonNode readPlan(String id) {
        String value = jdbc.queryForObject("SELECT watch_plan FROM assistant_conversation WHERE id=?", String.class, id);
        return decode(value);
    }
    private JsonNode decode(String value) {
        try { return value == null ? json.createObjectNode() : json.readTree(value); }
        catch (Exception ex) { throw new IllegalStateException("观影条件读取失败"); }
    }
    /** 读取当前用户保存的长期排除偏好。 */
    public JsonNode preferences(Long userId) {
        authenticated(userId);
        var rows = jdbc.query("SELECT preferences FROM assistant_preference WHERE user_id=?", (rs, n) -> decode(rs.getString(1)), userId);
        return rows.isEmpty() ? json.createObjectNode() : rows.getFirst();
    }
    /** 校验并保存当前用户的长期排除偏好。 */
    @Transactional
    public JsonNode savePreferences(Long userId, JsonNode value) {
        authenticated(userId);
        try {
            AssistantWatchPlan.validate(value);
            value.fieldNames().forEachRemaining(key -> {
                if (!Set.of("excludedGenres", "excludedMovieIds").contains(key)) throw new IllegalArgumentException();
            });
        } catch (IllegalArgumentException ex) { throw new BusinessException(400, "仅支持保存有效的排除类型和影片"); }
        // Serialize first save/update for this account without accepting a client identity.
        jdbc.queryForObject("SELECT id FROM `user` WHERE id=? FOR UPDATE", Long.class, userId);
        if (jdbc.update("UPDATE assistant_preference SET preferences=?,update_time=NOW() WHERE user_id=?", encode(value), userId) == 0)
            jdbc.update("INSERT INTO assistant_preference(user_id,preferences) VALUES(?,?)", userId, encode(value));
        return value.deepCopy();
    }
    private Turn turn(ResultSet rs, int row) throws SQLException {
        try {
            return new Turn(rs.getLong("id"), rs.getString("question"), rs.getString("answer"), rs.getString("source"), rs.getString("status"),
                    json.readValue(rs.getString("tool_results"), new TypeReference<>() {}),
                    json.readValue(rs.getString("suggestions"), new TypeReference<>() {}), rs.getTimestamp("create_time").toLocalDateTime(), decode(rs.getString("watch_plan")));
        } catch (java.io.IOException ex) { throw new SQLException("对话记录读取失败"); }
    }
}
