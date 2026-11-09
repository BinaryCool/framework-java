package com.dodosupermarket.survey.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;

/**
 * 问卷定义契约：编辑器与服务端共用的、刻意收窄的 SurveyJS 兼容格式。
 * 负责问卷定义的校验与规范化、答卷答案校验、以及生成 SurveyJS 渲染 schema。
 * The editor and the server share this deliberately bounded SurveyJS-compatible contract.
 */
public final class SurveyDefinition {
    /**
     * 支持的题型白名单：单选/多选/下拉/单行文本/多行文本/评分/判断
     */
    private static final Set<String> TYPES = Set.of("radiogroup", "checkbox", "dropdown", "text", "comment", "rating", "boolean");

    private SurveyDefinition() {
    }

    /**
     * 读取并校验文本字段：超长、必填为空或含 NUL 字符时抛 400
     */
    private static String text(JsonNode node, String field, int max, boolean required) {
        JsonNode value = node.get(field);
        if (value != null && !value.isTextual()) throw SurveyException.invalid(field + " 必须是文本");
        String result = value == null ? "" : value.asText().trim();
        if (result.length() > max || (required && result.isEmpty()) || result.indexOf('\0') >= 0)
            throw SurveyException.invalid(field + " 长度不正确");
        return result;
    }

    /**
     * 读取并校验非负整数字段，缺省返回 0
     */
    private static long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return 0;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)
            throw SurveyException.invalid(field + " 必须是非负整数");
        return value.longValue();
    }

    /**
     * 校验并规范化问卷定义，产出可入库的标准结构。
     * 校验项：标题/描述长度、门店与起止时间、1~60 道题、题号唯一、题型白名单、
     * 选项数量与去重、显示条件必须引用前面的单选/下拉题及其选项。
     */
    public static ObjectNode normalize(JsonNode input) {
        if (input == null || !input.isObject()) throw SurveyException.invalid("问卷内容无效");
        ObjectNode d = JsonNodeFactory.instance.objectNode();
        d.put("title", text(input, "title", 120, true));
        d.put("description", text(input, "description", 2000, false));
        d.put("storeId", number(input, "storeId"));
        d.put("startsAt", number(input, "startsAt"));
        d.put("endsAt", number(input, "endsAt"));
        if (d.path("endsAt").asLong() > 0 && d.path("endsAt").asLong() <= d.path("startsAt").asLong())
            throw SurveyException.invalid("结束时间必须晚于开始时间");
        JsonNode questions = input.path("questions");
        if (!questions.isArray() || questions.size() < 1 || questions.size() > 60)
            throw SurveyException.invalid("每份问卷需包含 1 到 60 道题");
        ArrayNode clean = d.putArray("questions");
        Map<String, ObjectNode> previous = new LinkedHashMap<>();
        for (JsonNode raw : questions) {
            String name = text(raw, "name", 40, true), type = text(raw, "type", 20, true);
            if (!name.matches("[a-zA-Z][a-zA-Z0-9_]{0,39}") || previous.containsKey(name))
                throw SurveyException.invalid("题目编号无效或重复");
            if (!TYPES.contains(type)) throw SurveyException.invalid("暂不支持该题型");
            ObjectNode q = clean.addObject();
            q.put("name", name);
            q.put("title", text(raw, "title", 300, true));
            q.put("type", type);
            if (raw.has("required") && !raw.get("required").isBoolean()) throw SurveyException.invalid("必填设置无效");
            q.put("required", raw.path("required").asBoolean(false));
            if (Set.of("radiogroup", "checkbox", "dropdown").contains(type)) {
                JsonNode choices = raw.path("choices");
                if (!choices.isArray() || choices.size() < 2 || choices.size() > 30)
                    throw SurveyException.invalid("选项数量需为 2 到 30 个");
                Set<String> distinct = new HashSet<>();
                ArrayNode options = q.putArray("choices");
                for (JsonNode option : choices) {
                    if (!option.isTextual() || option.asText().trim().isEmpty() || option.asText().length() > 120 || !distinct.add(option.asText().trim()))
                        throw SurveyException.invalid("选项不能为空、重复或超过 120 字");
                    options.add(option.asText().trim());
                }
            }
            JsonNode condition = raw.get("condition");
            if (condition != null && !condition.isNull()) {
                String parent = text(condition, "question", 40, true), equals = text(condition, "equals", 120, true);
                ObjectNode referenced = previous.get(parent);
                if (referenced == null || !Set.of("radiogroup", "dropdown").contains(referenced.path("type").asText()) || !contains(referenced.path("choices"), TextNode.valueOf(equals)))
                    throw SurveyException.invalid("显示条件必须引用前面的单选题或下拉题及其选项");
                q.putObject("condition").put("question", parent).put("equals", equals);
            }
            previous.put(name, q);
        }
        return d;
    }

    /**
     * 判断选项数组中是否包含指定值
     */
    private static boolean contains(JsonNode options, JsonNode value) {
        for (JsonNode item : options) if (item.equals(value)) return true;
        return false;
    }

    /**
     * 按问卷定义校验并清洗答卷答案。
     * 拒绝未知题目与超长内容；逐题检查必填、显示条件（不满足则跳过该题）、按题型校验取值范围。
     */
    public static ObjectNode answers(JsonNode definition, JsonNode input) {
        if (input == null || !input.isObject() || input.size() > 60 || input.toString().length() > 131072)
            throw SurveyException.invalid("答卷内容无效或过长");
        Set<String> names = new HashSet<>();
        definition.path("questions").forEach(q -> names.add(q.path("name").asText()));
        input.fieldNames().forEachRemaining(n -> {
            if (!names.contains(n)) throw SurveyException.invalid("答卷包含未知题目");
        });
        ObjectNode clean = JsonNodeFactory.instance.objectNode();
        for (JsonNode q : definition.path("questions")) {
            String name = q.path("name").asText(), type = q.path("type").asText();
            JsonNode condition = q.get("condition");
            if (condition != null && !clean.path(condition.path("question").asText()).asText().equals(condition.path("equals").asText()))
                continue;
            JsonNode value = input.get(name);
            boolean empty = value == null || value.isNull() || (value.isTextual() && value.asText().trim().isEmpty()) || (value.isArray() && value.size() == 0);
            if (empty) {
                if (q.path("required").asBoolean()) throw SurveyException.invalid("请填写：" + q.path("title").asText());
                continue;
            }
            boolean valid;
            switch (type) {
                case "radiogroup":
                case "dropdown":
                    valid = value.isTextual() && contains(q.path("choices"), value);
                    break;
                case "checkbox":
                    valid = value.isArray() && value.size() <= q.path("choices").size();
                    Set<JsonNode> chosen = new HashSet<>();
                    for (JsonNode item : value) valid = valid && contains(q.path("choices"), item) && chosen.add(item);
                    break;
                case "rating":
                    valid = value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= 1 && value.asInt() <= 5;
                    break;
                case "boolean":
                    valid = value.isBoolean();
                    break;
                default:
                    valid = value.isTextual() && value.asText().length() <= (type.equals("text") ? 500 : 5000) && value.asText().indexOf('\0') < 0;
            }
            if (!valid) throw SurveyException.invalid("答案格式不正确：" + q.path("title").asText());
            clean.set(name, value);
        }
        return clean;
    }

    /**
     * 由问卷定义生成 SurveyJS 渲染 schema（中文界面文案、题号、字数上限、评分范围）。
     * 显示条件通过变量绑定实现，避免答案文本中的引号/反斜杠被当作表达式语法。
     */
    public static ObjectNode schema(JsonNode d) {
        ObjectNode s = JsonNodeFactory.instance.objectNode();
        s.set("title", d.path("title"));
        s.set("description", d.path("description"));
        s.put("locale", "zh-cn");
        s.put("completeText", "提交问卷");
        s.put("completedHtml", "<h3>提交成功，感谢你的反馈！</h3>");
        s.put("showQuestionNumbers", "on");
        s.put("clearInvisibleValues", "onHidden");
        ObjectNode variables = s.putObject("duduConditions");
        ArrayNode elements = s.putArray("elements");
        for (JsonNode q : d.path("questions")) {
            ObjectNode e = elements.addObject();
            for (String field : List.of("name", "type", "title", "choices"))
                if (q.has(field)) e.set(field, q.get(field));
            e.set("isRequired", q.path("required"));
            if (q.path("type").asText().equals("text")) e.put("maxLength", 500);
            if (q.path("type").asText().equals("comment")) e.put("maxLength", 5000);
            if (q.path("type").asText().equals("rating")) {
                e.put("rateMin", 1);
                e.put("rateMax", 5);
                e.put("rateStep", 1);
            }
            if (q.has("condition")) {
                // SurveyJS string literals do not implement JSON escaping. Bind a variable instead,
                // so quotes/backslashes in ordinary answer text never become expression syntax.
                String variable = "_duduExpected_" + q.path("name").asText();
                variables.set(variable, q.path("condition").path("equals"));
                e.put("visibleIf", "{" + q.path("condition").path("question").asText() + "} = {" + variable + "}");
            }
        }
        return s;
    }
}
