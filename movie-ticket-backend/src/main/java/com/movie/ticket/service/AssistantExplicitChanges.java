package com.movie.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.regex.Pattern;

/** A small set of unambiguous corrections are enforced before the model; general language stays with the model. */
public final class AssistantExplicitChanges {
    private AssistantExplicitChanges() {}
    public static ObjectNode parse(ObjectMapper json, String message) {
        var patch = json.createObjectNode();
        if (clause(message, "(?:先|这次)?(?:不用积分|不使用积分|不要用积分|不要使用积分|保留积分|积分先留着)(?:了)?")) patch.put("usePoints", false);
        if (clause(message, "(?:先|这次)?(?:不用优惠券|不使用优惠券|不要用券|保留优惠券)(?:了)?")) patch.put("useCoupons", false);
        var count = Pattern.compile("(?:^|[，,。；;\\s])(?:请|帮我)?(?:只|仅)?(?:推荐|给我|找|要)([一二两三123])(?:部|个|场|项)").matcher(message);
        if (count.find()) patch.put("limit", switch (count.group(1)) { case "一", "1" -> 1; case "二", "两", "2" -> 2; default -> 3; });
        var date = Pattern.compile("(?:换成|换到|改成|改到|改为)(今天|明天|后天)(?=$|[，,。；;\\s])").matcher(message);
        if (date.find()) {
            int days = date.group(1).equals("明天") ? 1 : date.group(1).equals("后天") ? 2 : 0;
            patch.put("date", LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(days).toString());
            patch.putNull("showtimeId");
        }
        return patch;
    }
    private static boolean clause(String message, String words) {
        return Pattern.compile("(?:^|[，,。；;\\s])" + words + "(?=$|[，,。；;\\s])").matcher(message).find();
    }
    public static boolean supportsScene(String scene, String message) {
        String words = switch (scene) {
            case "DATE" -> "约会|情侣|伴侣|男友|女友|男朋友|女朋友|对象|恋人|爱人|妻子|丈夫|老公|老婆";
            case "RELAXED" -> "轻松|放松|累|解压|开心|搞笑|治愈|不想动脑";
            case "FAMILY" -> "小孩|孩子|家人|父母|爸|妈|老人|亲子|全家|一家|宝宝|儿子|女儿";
            case "FRIENDS" -> "朋友|同学|同事|聚会|室友|哥们|闺蜜|兄弟";
            case "SOLO" -> "一个人|独自|自己看|单人";
            default -> "(?!)";
        };
        return Pattern.compile(words).matcher(message).find();
    }
}
