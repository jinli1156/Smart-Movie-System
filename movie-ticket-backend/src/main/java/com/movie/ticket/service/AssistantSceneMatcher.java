package com.movie.ticket.service;

import java.util.*;

/** Soft scenario evidence from actual catalog genres; never a content rating or a safety certification. */
public final class AssistantSceneMatcher {
    private AssistantSceneMatcher() {}
    private static final Map<String, List<String>> GENRES = Map.of(
            "RELAXED", List.of("喜剧", "动画"), "DATE", List.of("爱情", "喜剧"),
            "FAMILY", List.of("家庭", "动画"), "FRIENDS", List.of("喜剧", "动作", "科幻"), "SOLO", List.of());
    public static int score(String scene, String genre) {
        if (genre == null) return 0;
        return (int) GENRES.getOrDefault(scene, List.of()).stream().filter(genre::contains).count();
    }
    public static String evidence(String scene, String genre) {
        var matches = GENRES.getOrDefault(scene, List.of()).stream().filter(g -> genre != null && genre.contains(g)).toList();
        if (matches.isEmpty()) return "按真实片单筛选；缺少足够的场景匹配依据，可继续说明喜欢的题材";
        return "本系统标注类型含“" + String.join("、", matches) + "”，作为场景偏好的软匹配依据";
    }
    public static final String NOTICE = "场景匹配依据本系统影片类型，不能证明适龄、无惊吓或无敏感内容；亲子观影请另行核实内容提示。";
}
