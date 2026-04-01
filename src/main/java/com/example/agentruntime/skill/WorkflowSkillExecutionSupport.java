package com.example.agentruntime.skill;

import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityRegistry;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.document.CapabilityDocumentService;
import com.example.agentruntime.document.CapabilityDocumentView;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workflow Skill 执行支持服务。
 * 负责处理条件判断、模板渲染、能力绑定调用和步骤状态写回，
 * 避免把这些运行时细节都堆进 WorkflowSkill 本体里。
 */
@Component
public class WorkflowSkillExecutionSupport {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");
    private final ObjectProvider<CapabilityRegistry> capabilityRegistryProvider;
    private final ObjectProvider<CapabilityDocumentService> capabilityDocumentServiceProvider;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public WorkflowSkillExecutionSupport(ObjectProvider<CapabilityRegistry> capabilityRegistryProvider,
                                         ObjectProvider<CapabilityDocumentService> capabilityDocumentServiceProvider,
                                         ObjectMapper objectMapper,
                                         MessageService messageService) {
        this.capabilityRegistryProvider = capabilityRegistryProvider;
        this.capabilityDocumentServiceProvider = capabilityDocumentServiceProvider;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    /**
     * 渲染步骤 instruction / when / capabilityInput 中的模板变量。
     * 当前支持用户消息、工作区、输入对象和前序步骤产物。
     */
    public String renderTemplate(String template, CapabilityContext context, JsonNode input, ObjectNode workflowState) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String expression = matcher.group(1);
            String replacement = resolveExpression(expression, context, input, workflowState);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /**
     * 计算步骤条件是否满足。
     * 当前采用轻量真值规则：空字符串 / false / no / 0 视为不执行，其余视为执行。
     */
    public boolean shouldExecute(SkillWorkflowStep step, CapabilityContext context, JsonNode input, ObjectNode workflowState) {
        if (step.when() == null || step.when().isBlank()) {
            return true;
        }
        Boolean expressionResult = evaluateConditionExpression(step.when(), context, input, workflowState);
        if (expressionResult != null) {
            return expressionResult;
        }
        String rendered = renderTemplate(step.when(), context, input, workflowState).trim();
        if (rendered.isEmpty()) {
            return false;
        }
        String normalized = rendered.toLowerCase();
        return !("false".equals(normalized)
                || "0".equals(normalized)
                || "no".equals(normalized)
                || "null".equals(normalized)
                || "off".equals(normalized));
    }

    /**
     * 递归渲染 capabilityInput 里的字符串模板，生成真正用于工具调用的输入对象。
     */
    public JsonNode renderCapabilityInput(Object capabilityInputTemplate,
                                          CapabilityContext context,
                                          JsonNode input,
                                          ObjectNode workflowState) {
        if (capabilityInputTemplate == null) {
            return objectMapper.createObjectNode();
        }
        JsonNode templateNode = objectMapper.valueToTree(capabilityInputTemplate);
        return renderNode(templateNode, context, input, workflowState);
    }

    /**
     * 执行 workflow 绑定的能力。
     * 这里通过 ObjectProvider 按需取 CapabilityRegistry，避免在 Skill provider 初始化阶段形成循环依赖。
     */
    public CapabilityResult invokeCapability(String currentSkillCapabilityId,
                                             SkillWorkflowStep step,
                                             CapabilityContext context,
                                             JsonNode renderedInput) {
        if (step.capabilityId() == null || step.capabilityId().isBlank()) {
            return null;
        }
        if (step.capabilityId().equals(currentSkillCapabilityId)) {
            throw new IllegalStateException(messageService.get("skill.workflow.error.selfInvocation", currentSkillCapabilityId));
        }
        CapabilityRegistry registry = capabilityRegistryProvider.getIfAvailable();
        if (registry == null) {
            throw new IllegalStateException(messageService.get("skill.workflow.error.registryUnavailable"));
        }
        AgentCapability capability = registry.get(step.capabilityId())
                .orElseThrow(() -> new IllegalArgumentException(
                        messageService.get("skill.workflow.error.capabilityNotFound", step.capabilityId())
                ));
        return capability.execute(context, renderedInput);
    }

    /**
     * 判断任意 when 表达式是否命中。
     * 这个入口主要给 workflow 的派生能力使用，例如文档摘要读完后再决定是否继续加载 detail。
     */
    public boolean evaluateWhenExpression(String when,
                                          CapabilityContext context,
                                          JsonNode input,
                                          ObjectNode workflowState) {
        if (when == null || when.isBlank()) {
            return true;
        }
        SkillWorkflowStep probeStep = new SkillWorkflowStep(
                "condition-probe",
                "condition-probe",
                "",
                "condition-probe",
                when,
                null,
                null,
                null,
                null,
                null,
                null,
                false
        );
        return shouldExecute(probeStep, context, input, workflowState);
    }

    /**
     * 执行 workflow 中显式声明的能力文档读取步骤。
     * 约定先读取 summary，再根据 documentDetailWhen 决定是否继续读取 detail。
     */
    public CapabilityResult readCapabilityDocumentStep(SkillWorkflowStep step,
                                                       CapabilityContext context,
                                                       JsonNode input,
                                                       ObjectNode workflowState) {
        CapabilityDocumentService documentService = capabilityDocumentServiceProvider.getIfAvailable();
        if (documentService == null) {
            throw new IllegalStateException(messageService.get("skill.workflow.error.documentServiceUnavailable"));
        }

        String renderedDocId = renderTemplate(step.documentDocId(), context, input, workflowState).trim();
        if (renderedDocId.isBlank()) {
            throw new IllegalArgumentException(messageService.get("skill.workflow.error.documentIdRequired"));
        }

        CapabilityDocumentView summary = documentService.read(renderedDocId, "summary");
        ObjectNode output = objectMapper.createObjectNode();
        output.put("docId", renderedDocId);
        output.put("summaryLoaded", true);
        output.put("detailLoaded", false);
        output.set("summary", documentToNode(summary));
        output.set("detail", JsonNodeFactory.instance.nullNode());

        ObjectNode simulatedState = workflowState.deepCopy();
        simulatedState.set(step.outputKey(), output.deepCopy());
        simulatedState.set(step.id(), output.deepCopy());

        if (step.documentDetailWhen() != null && evaluateWhenExpression(step.documentDetailWhen(), context, input, simulatedState)) {
            CapabilityDocumentView detail = documentService.read(renderedDocId, "detail");
            output.put("detailLoaded", true);
            output.set("detail", documentToNode(detail));
        }

        return CapabilityResult.success(messageService.get("capability.builtin.docRead.success"), output);
    }

    /**
     * 将步骤产物写入 workflow 状态，供后续步骤引用。
     */
    public void rememberStepOutput(ObjectNode workflowState, SkillWorkflowStep step, JsonNode value) {
        if (workflowState == null || step == null || value == null) {
            return;
        }
        workflowState.set(step.outputKey(), value);
        workflowState.set(step.id(), value);
    }

    /**
     * 将步骤元信息写入 workflow 状态。
     * 这里会额外写入 `xxxMeta` 节点，方便后续步骤按 success / message / status 做条件分支。
     */
    public void rememberStepMeta(ObjectNode workflowState, SkillWorkflowStep step, JsonNode meta) {
        if (workflowState == null || step == null || meta == null) {
            return;
        }
        workflowState.set(step.outputKey() + "Meta", meta);
        workflowState.set(step.id() + "Meta", meta);
    }

    /**
     * 构建步骤元信息对象。
     * 对于能力绑定步骤，这里会保留 success、message、status 和 output，方便后续条件判断与调试回放。
     */
    public ObjectNode buildStepMeta(SkillWorkflowStep step, CapabilityResult capabilityResult, String status) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("stepId", step.id());
        meta.put("outputKey", step.outputKey());
        meta.put("status", status);
        meta.put("success", capabilityResult != null && capabilityResult.success());
        if (step.capabilityId() != null) {
            meta.put("capabilityId", step.capabilityId());
        }
        if (step.documentDocId() != null) {
            meta.put("documentDocId", step.documentDocId());
        }
        if (capabilityResult != null) {
            meta.put("message", safe(capabilityResult.message()));
            meta.set("output", capabilityResult.output() == null ? JsonNodeFactory.instance.nullNode() : capabilityResult.output());
        }
        return meta;
    }

    private JsonNode renderNode(JsonNode node, CapabilityContext context, JsonNode input, ObjectNode workflowState) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isTextual()) {
            return JsonNodeFactory.instance.textNode(renderTemplate(node.asText(), context, input, workflowState));
        }
        if (node.isObject()) {
            ObjectNode rendered = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                rendered.set(entry.getKey(), renderNode(entry.getValue(), context, input, workflowState));
            }
            return rendered;
        }
        if (node.isArray()) {
            ArrayNode rendered = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                rendered.add(renderNode(item, context, input, workflowState));
            }
            return rendered;
        }
        return node.deepCopy();
    }

    /**
     * 解析模板表达式。
     * 当前支持：
     * - {{userMessage}}
     * - {{workspaceRoot}}
     * - {{input.xxx.yyy}}
     * - {{state.xxx.yyy}}
     */
    private String resolveExpression(String expression, CapabilityContext context, JsonNode input, ObjectNode workflowState) {
        String normalized = expression == null ? "" : expression.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if ("userMessage".equals(normalized)) {
            return safe(context.userMessage());
        }
        if ("workspaceRoot".equals(normalized)) {
            return safe(context.workspaceRoot());
        }
        if ("input".equals(normalized)) {
            return input == null || input.isNull() ? "{}" : input.toString();
        }
        if ("state".equals(normalized)) {
            return workflowState == null ? "{}" : workflowState.toString();
        }
        if (normalized.startsWith("input.")) {
            return stringify(resolvePath(input, normalized.substring("input.".length())));
        }
        if (normalized.startsWith("state.")) {
            return stringify(resolvePath(workflowState, normalized.substring("state.".length())));
        }
        return "";
    }

    /**
     * 解析点路径。
     * 这样 workflow 可以用 `state.echoResultMeta.success` 这类形式读取前序步骤的元信息。
     */
    private JsonNode resolvePath(JsonNode root, String path) {
        if (root == null || root.isNull() || path == null || path.isBlank()) {
            return JsonNodeFactory.instance.nullNode();
        }
        JsonNode current = root;
        String[] segments = path.split("\\.");
        for (String segment : segments) {
            if (current == null || current.isNull() || current.isMissingNode()) {
                return JsonNodeFactory.instance.nullNode();
            }
            current = current.path(segment);
        }
        return current == null ? JsonNodeFactory.instance.nullNode() : current;
    }

    /**
     * 解析 workflow 条件表达式。
     * 当前支持：
     * - `==` / `!=`
     * - `>` / `>=` / `<` / `<=`
     * - `in`
     * - `!`
     * - `&&` / `||`
     * - `(` / `)` 优先级
     * 如果表达式本身不属于条件语法，则返回 null，交给旧的真值规则兜底。
     */
    private Boolean evaluateConditionExpression(String expression,
                                                CapabilityContext context,
                                                JsonNode input,
                                                ObjectNode workflowState) {
        if (!looksLikeConditionExpression(expression)) {
            return null;
        }
        try {
            ConditionParser parser = new ConditionParser(tokenizeConditionExpression(expression), context, input, workflowState);
            return parser.parse();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 判断字符串是否值得按条件表达式解析。
     * 这样可以避免把普通模板文本误判成条件语法。
     */
    private boolean looksLikeConditionExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        return expression.contains("&&")
                || expression.contains("||")
                || expression.contains("==")
                || expression.contains("!=")
                || expression.contains(">=")
                || expression.contains("<=")
                || expression.contains(">")
                || expression.contains("<")
                || expression.contains("!")
                || expression.contains(" in ")
                || expression.contains("(")
                || expression.contains(")");
    }

    /**
     * 把条件表达式切成轻量 token。
     * 这里会保护引号字符串和 `{{...}}` 模板，避免其中的符号被误拆。
     */
    private java.util.List<ConditionToken> tokenizeConditionExpression(String expression) {
        java.util.List<ConditionToken> tokens = new java.util.ArrayList<>();
        int index = 0;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == ',') {
                tokens.add(new ConditionToken(ConditionTokenType.COMMA, ","));
                index++;
                continue;
            }
            if (current == '(') {
                tokens.add(new ConditionToken(ConditionTokenType.LEFT_PAREN, "("));
                index++;
                continue;
            }
            if (current == '!') {
                if (index + 1 < expression.length() && expression.charAt(index + 1) == '=') {
                    // `!=` 会在双字符操作符分支里处理，这里不重复消费。
                } else {
                    tokens.add(new ConditionToken(ConditionTokenType.NOT, "!"));
                    index++;
                    continue;
                }
            }
            if (current == ')') {
                tokens.add(new ConditionToken(ConditionTokenType.RIGHT_PAREN, ")"));
                index++;
                continue;
            }
            if (index + 2 < expression.length()) {
                String triple = expression.substring(index, index + 3);
                if ("===".equals(triple)) {
                    tokens.add(new ConditionToken(ConditionTokenType.STRICT_EQUALS, triple));
                    index += 3;
                    continue;
                }
                if ("!==".equals(triple)) {
                    tokens.add(new ConditionToken(ConditionTokenType.STRICT_NOT_EQUALS, triple));
                    index += 3;
                    continue;
                }
            }
            if (index + 1 < expression.length()) {
                String pair = expression.substring(index, index + 2);
                if ("&&".equals(pair)) {
                    tokens.add(new ConditionToken(ConditionTokenType.AND, pair));
                    index += 2;
                    continue;
                }
                if ("||".equals(pair)) {
                    tokens.add(new ConditionToken(ConditionTokenType.OR, pair));
                    index += 2;
                    continue;
                }
                if ("==".equals(pair)) {
                    tokens.add(new ConditionToken(ConditionTokenType.EQUALS, pair));
                    index += 2;
                    continue;
                }
                if ("!=".equals(pair)) {
                    tokens.add(new ConditionToken(ConditionTokenType.NOT_EQUALS, pair));
                    index += 2;
                    continue;
                }
                if (">=".equals(pair)) {
                    tokens.add(new ConditionToken(ConditionTokenType.GREATER_THAN_OR_EQUALS, pair));
                    index += 2;
                    continue;
                }
                if ("<=".equals(pair)) {
                    tokens.add(new ConditionToken(ConditionTokenType.LESS_THAN_OR_EQUALS, pair));
                    index += 2;
                    continue;
                }
            }
            if (current == '>') {
                tokens.add(new ConditionToken(ConditionTokenType.GREATER_THAN, ">"));
                index++;
                continue;
            }
            if (current == '<') {
                tokens.add(new ConditionToken(ConditionTokenType.LESS_THAN, "<"));
                index++;
                continue;
            }
            if (startsWithKeyword(expression, index, "not")) {
                int cursor = index + 3;
                while (cursor < expression.length() && Character.isWhitespace(expression.charAt(cursor))) {
                    cursor++;
                }
                if (startsWithKeyword(expression, cursor, "in")) {
                    tokens.add(new ConditionToken(ConditionTokenType.NOT_IN, "not in"));
                    index = cursor + 2;
                    continue;
                }
            }
            if (startsWithKeyword(expression, index, "in")) {
                tokens.add(new ConditionToken(ConditionTokenType.IN, "in"));
                index += 2;
                continue;
            }
            int nextIndex = readConditionValue(expression, index);
            if (nextIndex <= index) {
                throw new IllegalArgumentException("Invalid condition token");
            }
            tokens.add(new ConditionToken(ConditionTokenType.VALUE, expression.substring(index, nextIndex).trim()));
            index = nextIndex;
        }
        return tokens;
    }

    /**
     * 读取一个条件值 token。
     * 支持普通路径、带引号文本，以及 `{{...}}` 模板占位。
     */
    private int readConditionValue(String expression, int startIndex) {
        char current = expression.charAt(startIndex);
        if (current == '"' || current == '\'') {
            return readQuotedValue(expression, startIndex, current);
        }
        if (current == '[') {
            return readBracketValue(expression, startIndex);
        }
        if (current == '{' && startIndex + 1 < expression.length() && expression.charAt(startIndex + 1) == '{') {
            return readTemplateValue(expression, startIndex);
        }
        int index = startIndex;
        while (index < expression.length()) {
            char valueChar = expression.charAt(index);
            if (Character.isWhitespace(valueChar) || valueChar == '(' || valueChar == ')') {
                break;
            }
            if (index + 1 < expression.length()) {
                String pair = expression.substring(index, index + 2);
                if ("&&".equals(pair)
                        || "||".equals(pair)
                        || "==".equals(pair)
                        || "!=".equals(pair)
                        || ">=".equals(pair)
                        || "<=".equals(pair)) {
                    break;
                }
            }
            if (index + 2 < expression.length()) {
                String triple = expression.substring(index, index + 3);
                if ("===".equals(triple) || "!==".equals(triple)) {
                    break;
                }
            }
            if (valueChar == '!' || valueChar == '>' || valueChar == '<' || valueChar == ',') {
                break;
            }
            if (startsWithKeyword(expression, index, "not")) {
                int cursor = index + 3;
                while (cursor < expression.length() && Character.isWhitespace(expression.charAt(cursor))) {
                    cursor++;
                }
                if (startsWithKeyword(expression, cursor, "in")) {
                    break;
                }
            }
            if (startsWithKeyword(expression, index, "in")) {
                break;
            }
            index++;
        }
        return index;
    }

    private int readQuotedValue(String expression, int startIndex, char quote) {
        int index = startIndex + 1;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (current == quote && expression.charAt(index - 1) != '\\') {
                return index + 1;
            }
            index++;
        }
        throw new IllegalArgumentException("Unclosed quoted condition value");
    }

    private int readTemplateValue(String expression, int startIndex) {
        int index = startIndex + 2;
        while (index + 1 < expression.length()) {
            if (expression.charAt(index) == '}' && expression.charAt(index + 1) == '}') {
                return index + 2;
            }
            index++;
        }
        throw new IllegalArgumentException("Unclosed template condition value");
    }

    /**
     * 读取集合字面量。
     * 这样 `in` 右侧就可以写成 `[SUCCESS, DEGRADED]` 或 `['a', '{{state.x}}']`。
     */
    private int readBracketValue(String expression, int startIndex) {
        int depth = 1;
        int index = startIndex + 1;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (current == '"' || current == '\'') {
                index = readQuotedValue(expression, index, current);
                continue;
            }
            if (current == '{' && index + 1 < expression.length() && expression.charAt(index + 1) == '{') {
                index = readTemplateValue(expression, index);
                continue;
            }
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
            }
            index++;
        }
        throw new IllegalArgumentException("Unclosed collection condition value");
    }

    private boolean startsWithKeyword(String expression, int index, String keyword) {
        if (index < 0 || keyword == null || keyword.isBlank()) {
            return false;
        }
        if (!expression.regionMatches(index, keyword, 0, keyword.length())) {
            return false;
        }
        int endIndex = index + keyword.length();
        boolean leftBoundary = index == 0 || !isIdentifierPart(expression.charAt(index - 1));
        boolean rightBoundary = endIndex >= expression.length() || !isIdentifierPart(expression.charAt(endIndex));
        return leftBoundary && rightBoundary;
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '.';
    }

    /**
     * 解析条件表达式的单侧操作数，并尽量保留类型信息。
     * 当前支持：
     * 1. `state.xxx` / `input.xxx` 路径
     * 2. `{{state.xxx}}` 这类模板占位
     * 3. 直接字面量：字符串 / 布尔 / 数字 / null
     */
    private Object resolveConditionValue(String rawOperand,
                                         CapabilityContext context,
                                         JsonNode input,
                                         ObjectNode workflowState) {
        String operand = rawOperand == null ? "" : rawOperand.trim();
        if (operand.isEmpty()) {
            return "";
        }
        if ((operand.startsWith("\"") && operand.endsWith("\""))
                || (operand.startsWith("'") && operand.endsWith("'"))) {
            String inner = operand.substring(1, operand.length() - 1).trim();
            if (inner.startsWith("{{") && inner.endsWith("}}")) {
                String expression = inner.substring(2, inner.length() - 2).trim();
                return resolveExpressionValue(expression, context, input, workflowState);
            }
            return inner;
        }
        if (operand.startsWith("{{") && operand.endsWith("}}")) {
            String inner = operand.substring(2, operand.length() - 2).trim();
            return resolveExpressionValue(inner, context, input, workflowState);
        }
        if ("userMessage".equals(operand)
                || "workspaceRoot".equals(operand)
                || "input".equals(operand)
                || "state".equals(operand)
                || operand.startsWith("input.")
                || operand.startsWith("state.")) {
            return resolveExpressionValue(operand, context, input, workflowState);
        }
        if (looksLikeFunctionInvocation(operand)) {
            return parseValueExpression(operand, context, input, workflowState);
        }
        if (operand.startsWith("[") && operand.endsWith("]")) {
            return parseCollectionLiteral(operand, context, input, workflowState);
        }
        Object literalValue = parseLiteralValue(operand);
        return literalValue == null && !"null".equalsIgnoreCase(operand) ? operand : literalValue;
    }

    private Object resolveExpressionValue(String expression,
                                          CapabilityContext context,
                                          JsonNode input,
                                          ObjectNode workflowState) {
        String normalized = expression == null ? "" : expression.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if ("userMessage".equals(normalized)) {
            return safe(context.userMessage());
        }
        if ("workspaceRoot".equals(normalized)) {
            return safe(context.workspaceRoot());
        }
        if ("input".equals(normalized)) {
            return input == null ? JsonNodeFactory.instance.nullNode() : input;
        }
        if ("state".equals(normalized)) {
            return workflowState == null ? JsonNodeFactory.instance.nullNode() : workflowState;
        }
        if (normalized.startsWith("input.")) {
            return toTypedConditionValue(resolvePath(input, normalized.substring("input.".length())));
        }
        if (normalized.startsWith("state.")) {
            return toTypedConditionValue(resolvePath(workflowState, normalized.substring("state.".length())));
        }
        return resolveExpression(normalized, context, input, workflowState);
    }

    private Object toTypedConditionValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        return node.deepCopy();
    }

    private Object parseLiteralValue(String operand) {
        if (operand == null) {
            return "";
        }
        if ("true".equalsIgnoreCase(operand)) {
            return true;
        }
        if ("false".equalsIgnoreCase(operand)) {
            return false;
        }
        if ("null".equalsIgnoreCase(operand)) {
            return null;
        }
        BigDecimal numeric = parseNumber(operand);
        if (numeric != null) {
            return numeric;
        }
        return operand;
    }

    /**
     * 解析单个值表达式。
     * 这个入口主要用于支持独立函数调用和集合字面量内部的嵌套函数。
     */
    private Object parseValueExpression(String expression,
                                        CapabilityContext context,
                                        JsonNode input,
                                        ObjectNode workflowState) {
        ConditionParser parser = new ConditionParser(tokenizeConditionExpression(expression), context, input, workflowState);
        return parser.parseValueExpression();
    }

    private boolean looksLikeFunctionInvocation(String operand) {
        if (operand == null || operand.isBlank()) {
            return false;
        }
        int leftParen = operand.indexOf('(');
        int rightParen = operand.lastIndexOf(')');
        if (leftParen <= 0 || rightParen != operand.length() - 1) {
            return false;
        }
        return isFunctionName(operand.substring(0, leftParen).trim());
    }

    /**
     * 解析集合字面量。
     * 这里会递归解析集合内的模板、路径和函数表达式，供 in / containsAny 等集合函数直接复用。
     */
    private List<Object> parseCollectionLiteral(String operand,
                                                CapabilityContext context,
                                                JsonNode input,
                                                ObjectNode workflowState) {
        String content = operand.substring(1, operand.length() - 1).trim();
        if (content.isEmpty()) {
            return List.of();
        }
        List<Object> items = new ArrayList<>();
        for (String item : splitCollectionItems(content)) {
            items.add(resolveConditionValue(item, context, input, workflowState));
        }
        return items;
    }

    /**
     * 把单个值解释成布尔真值。
     * 这里与 shouldExecute 的兜底规则保持一致，并且额外支持布尔、数值、集合和对象类型。
     */
    private boolean toBooleanValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof BigDecimal numericValue) {
            return numericValue.compareTo(BigDecimal.ZERO) != 0;
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        if (value instanceof JsonNode node) {
            if (node.isArray()) {
                return node.size() > 0;
            }
            if (node.isObject()) {
                return node.size() > 0;
            }
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            if (node.isNumber()) {
                return node.decimalValue().compareTo(BigDecimal.ZERO) != 0;
            }
            return toBooleanValue(node.asText());
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        return !(normalized.isEmpty()
                || "false".equals(normalized)
                || "0".equals(normalized)
                || "no".equals(normalized)
                || "null".equals(normalized)
                || "off".equals(normalized));
    }

    /**
     * 判断左值是否属于右侧集合。
     * 集合语法使用 `[a, b, c]`，支持字面量、模板和路径表达式。
     */
    private boolean matchesInCollection(Object leftValue,
                                        Object rightValue,
                                        CapabilityContext context,
                                        JsonNode input,
                                        ObjectNode workflowState) {
        for (Object item : toCollectionValues(rightValue, context, input, workflowState)) {
            if (strictEquals(leftValue, item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 轻量拆分集合元素。
     * 需要保护引号、模板和嵌套集合，避免值内部的逗号被误拆。
     */
    private String[] splitCollectionItems(String content) {
        java.util.List<String> items = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int index = 0;
        while (index < content.length()) {
            char value = content.charAt(index);
            if (!inDoubleQuote && value == '\'' && (index == 0 || content.charAt(index - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
                current.append(value);
                index++;
                continue;
            }
            if (!inSingleQuote && value == '"' && (index == 0 || content.charAt(index - 1) != '\\')) {
                inDoubleQuote = !inDoubleQuote;
                current.append(value);
                index++;
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote) {
                if (value == '[') {
                    bracketDepth++;
                } else if (value == ']') {
                    bracketDepth--;
                } else if (value == ',' && bracketDepth == 0) {
                    items.add(current.toString().trim());
                    current.setLength(0);
                    index++;
                    continue;
                }
            }
            current.append(value);
            index++;
        }
        if (!current.isEmpty()) {
            items.add(current.toString().trim());
        }
        return items.stream()
                .filter(item -> !item.isBlank())
                .toArray(String[]::new);
    }

    /**
     * 解析大小比较。
     * 当前优先走数值比较；如果两侧都不是数值，则回退为忽略大小写的字符串比较。
     */
    private int compareOperands(Object leftOperand, Object rightOperand) {
        BigDecimal leftNumber = parseNumber(leftOperand);
        BigDecimal rightNumber = parseNumber(rightOperand);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }
        TemporalComparable leftTemporal = parseTemporalComparable(leftOperand);
        TemporalComparable rightTemporal = parseTemporalComparable(rightOperand);
        if (leftTemporal != null && rightTemporal != null) {
            return leftTemporal.sortValue().compareTo(rightTemporal.sortValue());
        }
        return toStringValue(leftOperand).compareToIgnoreCase(toStringValue(rightOperand));
    }

    private BigDecimal parseNumber(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof BigDecimal decimalValue) {
            return decimalValue;
        }
        if (rawValue instanceof Number numberValue) {
            return new BigDecimal(String.valueOf(numberValue));
        }
        if (rawValue instanceof JsonNode node && node.isNumber()) {
            return node.decimalValue();
        }
        String normalized = String.valueOf(rawValue).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean looseEquals(Object leftValue, Object rightValue) {
        BigDecimal leftNumber = parseNumber(leftValue);
        BigDecimal rightNumber = parseNumber(rightValue);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber) == 0;
        }
        TemporalComparable leftTemporal = parseTemporalComparable(leftValue);
        TemporalComparable rightTemporal = parseTemporalComparable(rightValue);
        if (leftTemporal != null && rightTemporal != null) {
            return leftTemporal.sortValue().compareTo(rightTemporal.sortValue()) == 0;
        }
        return toStringValue(leftValue).equalsIgnoreCase(toStringValue(rightValue));
    }

    private boolean strictEquals(Object leftValue, Object rightValue) {
        if (leftValue == null || rightValue == null) {
            return leftValue == rightValue;
        }
        if (leftValue instanceof BigDecimal leftNumber && rightValue instanceof BigDecimal rightNumber) {
            return leftNumber.compareTo(rightNumber) == 0;
        }
        if (leftValue instanceof JsonNode leftNode && rightValue instanceof JsonNode rightNode) {
            return leftNode.equals(rightNode);
        }
        if (!leftValue.getClass().equals(rightValue.getClass())) {
            return false;
        }
        return leftValue.equals(rightValue);
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JsonNode node) {
            if (node.isTextual()) {
                return node.asText();
            }
            return node.toString();
        }
        return String.valueOf(value);
    }

    private boolean isFunctionName(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }
        for (int index = 0; index < tokenValue.length(); index++) {
            char current = tokenValue.charAt(index);
            if (!Character.isLetterOrDigit(current) && current != '_' && current != '-') {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行轻量函数式条件。
     * 当前优先提供 workflow 编排里最常用的一组函数，避免一开始就引入过重 DSL。
     */
    private Object evaluateFunction(String functionName, java.util.List<Object> arguments) {
        String normalized = functionName == null ? "" : functionName.trim().toLowerCase();
        return switch (normalized) {
            case "exists" -> requireArgCount(normalized, arguments, 1) && !isMissing(arguments.getFirst());
            case "empty" -> requireArgCount(normalized, arguments, 1) && isEmpty(arguments.getFirst());
            case "contains" -> containsFunction(arguments);
            case "containsany" -> containsAnyFunction(arguments);
            case "containsall" -> containsAllFunction(arguments);
            case "intersects" -> intersectsFunction(arguments);
            case "startswith" -> requireArgCount(normalized, arguments, 2)
                    && toStringValue(arguments.getFirst()).startsWith(toStringValue(arguments.get(1)));
            case "endswith" -> requireArgCount(normalized, arguments, 2)
                    && toStringValue(arguments.getFirst()).endsWith(toStringValue(arguments.get(1)));
            case "matches" -> requireArgCount(normalized, arguments, 2)
                    && toStringValue(arguments.getFirst()).matches(toStringValue(arguments.get(1)));
            case "length" -> lengthFunction(arguments);
            case "count" -> lengthFunction(arguments);
            case "number" -> requireArgCount(normalized, arguments, 1) ? parseNumber(arguments.getFirst()) : null;
            case "boolean" -> requireArgCount(normalized, arguments, 1) && toBooleanValue(arguments.getFirst());
            case "string" -> requireArgCount(normalized, arguments, 1) ? toStringValue(arguments.getFirst()) : "";
            case "typeof" -> requireArgCount(normalized, arguments, 1) ? valueTypeOf(arguments.getFirst()) : "unknown";
            case "date" -> dateFunction(arguments);
            case "datetime" -> datetimeFunction(arguments);
            case "today" -> requireArgCount(normalized, arguments, 0) ? LocalDate.now(ZoneId.systemDefault()) : null;
            case "now" -> requireArgCount(normalized, arguments, 0) ? Instant.now() : null;
            case "daysbetween" -> daysBetweenFunction(arguments);
            default -> throw new IllegalArgumentException("Unsupported workflow function");
        };
    }

    private boolean requireArgCount(String functionName, java.util.List<Object> arguments, int size) {
        if (arguments.size() != size) {
            throw new IllegalArgumentException("Invalid workflow function arguments: " + functionName);
        }
        return true;
    }

    private boolean containsFunction(java.util.List<Object> arguments) {
        requireArgCount("contains", arguments, 2);
        Object container = arguments.getFirst();
        Object target = arguments.get(1);
        if (isCollectionLike(container)) {
            List<Object> collection = toCollectionValues(container, null, null, null);
            return collection.stream().anyMatch(item -> strictEquals(item, target));
        }
        return toStringValue(container).contains(toStringValue(target));
    }

    private boolean containsAnyFunction(List<Object> arguments) {
        requireArgCount("containsAny", arguments, 2);
        List<Object> leftValues = toCollectionValues(arguments.getFirst(), null, null, null);
        List<Object> rightValues = toCollectionValues(arguments.get(1), null, null, null);
        return rightValues.stream().anyMatch(target -> leftValues.stream().anyMatch(item -> strictEquals(item, target)));
    }

    private boolean containsAllFunction(List<Object> arguments) {
        requireArgCount("containsAll", arguments, 2);
        List<Object> leftValues = toCollectionValues(arguments.getFirst(), null, null, null);
        List<Object> rightValues = toCollectionValues(arguments.get(1), null, null, null);
        return !rightValues.isEmpty()
                && rightValues.stream().allMatch(target -> leftValues.stream().anyMatch(item -> strictEquals(item, target)));
    }

    private boolean intersectsFunction(List<Object> arguments) {
        requireArgCount("intersects", arguments, 2);
        List<Object> leftValues = toCollectionValues(arguments.getFirst(), null, null, null);
        List<Object> rightValues = toCollectionValues(arguments.get(1), null, null, null);
        return leftValues.stream().anyMatch(left -> rightValues.stream().anyMatch(right -> strictEquals(left, right)));
    }

    private BigDecimal lengthFunction(java.util.List<Object> arguments) {
        requireArgCount("length", arguments, 1);
        Object value = arguments.getFirst();
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof JsonNode node) {
            if (node.isArray() || node.isObject()) {
                return BigDecimal.valueOf(node.size());
            }
            return BigDecimal.valueOf(node.asText().length());
        }
        return BigDecimal.valueOf(toStringValue(value).length());
    }

    private boolean isMissing(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof JsonNode node) {
            return node.isNull() || node.isMissingNode()
                    || (node.isTextual() && node.asText().isBlank())
                    || (node.isArray() && node.isEmpty())
                    || (node.isObject() && node.isEmpty());
        }
        if (value instanceof Iterable<?> iterable) {
            return !iterable.iterator().hasNext();
        }
        return value instanceof String stringValue && stringValue.isBlank();
    }

    private boolean isEmpty(Object value) {
        if (isMissing(value)) {
            return true;
        }
        if (value instanceof BigDecimal numericValue) {
            return numericValue.compareTo(BigDecimal.ZERO) == 0;
        }
        return false;
    }

    private String valueTypeOf(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof LocalDate) {
            return "date";
        }
        if (value instanceof Instant || value instanceof OffsetDateTime || value instanceof ZonedDateTime || value instanceof LocalDateTime) {
            return "datetime";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof BigDecimal || value instanceof Number) {
            return "number";
        }
        if (value instanceof Iterable<?>) {
            return "array";
        }
        if (value instanceof JsonNode node) {
            if (node.isArray()) {
                return "array";
            }
            if (node.isObject()) {
                return "object";
            }
            if (node.isBoolean()) {
                return "boolean";
            }
            if (node.isNumber()) {
                return "number";
            }
            if (node.isNull() || node.isMissingNode()) {
                return "null";
            }
        }
        return "string";
    }

    /**
     * 把不同形态的集合输入统一转成值列表。
     * 既支持 JsonNode 数组，也支持 workflow 条件里常见的 `[a, b, c]` 轻量字面量。
     */
    private List<Object> toCollectionValues(Object value,
                                            CapabilityContext context,
                                            JsonNode input,
                                            ObjectNode workflowState) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream().map(this::normalizeCollectionItem).toList();
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            for (Object item : iterable) {
                items.add(normalizeCollectionItem(item));
            }
            return items;
        }
        if (value instanceof JsonNode node) {
            if (node.isArray()) {
                List<Object> items = new ArrayList<>();
                for (JsonNode item : node) {
                    items.add(toTypedConditionValue(item));
                }
                return items;
            }
            return List.of(toTypedConditionValue(node));
        }
        String text = toStringValue(value).trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            if (context != null || input != null || workflowState != null) {
                return parseCollectionLiteral(text, context, input, workflowState);
            }
            String content = text.substring(1, text.length() - 1).trim();
            if (content.isEmpty()) {
                return List.of();
            }
            List<Object> items = new ArrayList<>();
            for (String item : splitCollectionItems(content)) {
                items.add(parseLiteralValue(item));
            }
            return List.copyOf(items);
        }
        return List.of(value);
    }

    private Object normalizeCollectionItem(Object value) {
        if (value instanceof JsonNode node) {
            return toTypedConditionValue(node);
        }
        return value;
    }

    private boolean isCollectionLike(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Iterable<?>) {
            return true;
        }
        if (value instanceof JsonNode node) {
            return node.isArray();
        }
        String text = toStringValue(value).trim();
        return text.startsWith("[") && text.endsWith("]");
    }

    private LocalDate dateFunction(List<Object> arguments) {
        requireArgCount("date", arguments, 1);
        TemporalComparable temporalComparable = parseTemporalComparable(arguments.getFirst());
        if (temporalComparable == null) {
            return null;
        }
        if ("date".equals(temporalComparable.type()) && temporalComparable.originalValue() instanceof LocalDate localDate) {
            return localDate;
        }
        return Instant.ofEpochMilli(temporalComparable.sortValue().longValue())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private Instant datetimeFunction(List<Object> arguments) {
        requireArgCount("datetime", arguments, 1);
        TemporalComparable temporalComparable = parseTemporalComparable(arguments.getFirst());
        if (temporalComparable == null) {
            return null;
        }
        if (temporalComparable.originalValue() instanceof LocalDate localDate) {
            return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
        return Instant.ofEpochMilli(temporalComparable.sortValue().longValue());
    }

    private BigDecimal daysBetweenFunction(List<Object> arguments) {
        requireArgCount("daysBetween", arguments, 2);
        LocalDate start = coerceToLocalDate(arguments.getFirst());
        LocalDate end = coerceToLocalDate(arguments.get(1));
        if (start == null || end == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end));
    }

    private LocalDate coerceToLocalDate(Object value) {
        TemporalComparable temporalComparable = parseTemporalComparable(value);
        if (temporalComparable == null) {
            return null;
        }
        if (temporalComparable.originalValue() instanceof LocalDate localDate) {
            return localDate;
        }
        return Instant.ofEpochMilli(temporalComparable.sortValue().longValue())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private TemporalComparable parseTemporalComparable(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof JsonNode node) {
            if (node.isTextual()) {
                return parseTemporalComparable(node.asText());
            }
            return null;
        }
        if (rawValue instanceof LocalDate localDate) {
            return new TemporalComparable("date", BigDecimal.valueOf(localDate.toEpochDay()), localDate);
        }
        if (rawValue instanceof Instant instant) {
            return new TemporalComparable("datetime", BigDecimal.valueOf(instant.toEpochMilli()), instant);
        }
        if (rawValue instanceof OffsetDateTime offsetDateTime) {
            Instant instant = offsetDateTime.toInstant();
            return new TemporalComparable("datetime", BigDecimal.valueOf(instant.toEpochMilli()), instant);
        }
        if (rawValue instanceof ZonedDateTime zonedDateTime) {
            Instant instant = zonedDateTime.toInstant();
            return new TemporalComparable("datetime", BigDecimal.valueOf(instant.toEpochMilli()), instant);
        }
        if (rawValue instanceof LocalDateTime localDateTime) {
            Instant instant = localDateTime.atZone(ZoneId.systemDefault()).toInstant();
            return new TemporalComparable("datetime", BigDecimal.valueOf(instant.toEpochMilli()), instant);
        }
        String normalized = String.valueOf(rawValue).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            Instant instant = Instant.parse(normalized);
            return new TemporalComparable("datetime", BigDecimal.valueOf(instant.toEpochMilli()), instant);
        } catch (DateTimeParseException ignored) {
        }
        try {
            Instant instant = OffsetDateTime.parse(normalized).toInstant();
            return new TemporalComparable("datetime", BigDecimal.valueOf(instant.toEpochMilli()), instant);
        } catch (DateTimeParseException ignored) {
        }
        try {
            Instant instant = ZonedDateTime.parse(normalized).toInstant();
            return new TemporalComparable("datetime", BigDecimal.valueOf(instant.toEpochMilli()), instant);
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized);
            Instant instant = localDateTime.atZone(ZoneId.systemDefault()).toInstant();
            return new TemporalComparable("datetime", BigDecimal.valueOf(instant.toEpochMilli()), instant);
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDate localDate = LocalDate.parse(normalized);
            return new TemporalComparable("date", BigDecimal.valueOf(localDate.toEpochDay()), localDate);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private ObjectNode documentToNode(CapabilityDocumentView document) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("docId", document.docId());
        node.put("capabilityId", document.capabilityId());
        node.put("name", document.name());
        node.put("capabilityType", document.capabilityType());
        node.put("docType", document.docType());
        node.put("content", document.content());
        ArrayNode tags = node.putArray("tags");
        document.tags().forEach(tags::add);
        return node;
    }

    private record TemporalComparable(String type, BigDecimal sortValue, Object originalValue) {
    }

    private record ConditionToken(ConditionTokenType type, String value) {
    }

    private enum ConditionTokenType {
        LEFT_PAREN,
        RIGHT_PAREN,
        COMMA,
        NOT,
        AND,
        OR,
        EQUALS,
        NOT_EQUALS,
        STRICT_EQUALS,
        STRICT_NOT_EQUALS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUALS,
        LESS_THAN,
        LESS_THAN_OR_EQUALS,
        IN,
        NOT_IN,
        VALUE
    }

    /**
     * 轻量条件表达式解析器。
     * 使用递归下降方式支持括号、与、或以及简单比较，满足 workflow 当前编排复杂度即可。
     */
    private final class ConditionParser {

        private final java.util.List<ConditionToken> tokens;
        private final CapabilityContext context;
        private final JsonNode input;
        private final ObjectNode workflowState;
        private int index;

        private ConditionParser(java.util.List<ConditionToken> tokens,
                                CapabilityContext context,
                                JsonNode input,
                                ObjectNode workflowState) {
            this.tokens = tokens;
            this.context = context;
            this.input = input;
            this.workflowState = workflowState;
        }

        private boolean parse() {
            boolean result = parseOr();
            if (index != tokens.size()) {
                throw new IllegalArgumentException("Unexpected trailing condition token");
            }
            return result;
        }

        /**
         * 仅解析一个值表达式。
         * 这个入口用于支持集合字面量中的函数嵌套和独立函数调用。
         */
        private Object parseValueExpression() {
            Object value = parseValueOperand();
            if (index != tokens.size()) {
                throw new IllegalArgumentException("Unexpected trailing value token");
            }
            return value;
        }

        private boolean parseOr() {
            boolean result = parseAnd();
            while (match(ConditionTokenType.OR)) {
                boolean right = parseAnd();
                result = result || right;
            }
            return result;
        }

        private boolean parseAnd() {
            boolean result = parseUnary();
            while (match(ConditionTokenType.AND)) {
                boolean right = parseUnary();
                result = result && right;
            }
            return result;
        }

        private boolean parseUnary() {
            if (match(ConditionTokenType.NOT)) {
                return !parseUnary();
            }
            return parsePrimary();
        }

        private boolean parsePrimary() {
            if (match(ConditionTokenType.LEFT_PAREN)) {
                boolean result = parseOr();
                consume(ConditionTokenType.RIGHT_PAREN);
                return result;
            }
            return parseValueCondition();
        }

        private boolean parseValueCondition() {
            Object leftValue = parseValueOperand();
            if (match(ConditionTokenType.EQUALS)) {
                Object rightValue = parseValueOperand();
                return looseEquals(leftValue, rightValue);
            }
            if (match(ConditionTokenType.NOT_EQUALS)) {
                Object rightValue = parseValueOperand();
                return !looseEquals(leftValue, rightValue);
            }
            if (match(ConditionTokenType.STRICT_EQUALS)) {
                Object rightValue = parseValueOperand();
                return strictEquals(leftValue, rightValue);
            }
            if (match(ConditionTokenType.STRICT_NOT_EQUALS)) {
                Object rightValue = parseValueOperand();
                return !strictEquals(leftValue, rightValue);
            }
            if (match(ConditionTokenType.GREATER_THAN)) {
                Object rightValue = parseValueOperand();
                return compare(leftValue, rightValue) > 0;
            }
            if (match(ConditionTokenType.GREATER_THAN_OR_EQUALS)) {
                Object rightValue = parseValueOperand();
                return compare(leftValue, rightValue) >= 0;
            }
            if (match(ConditionTokenType.LESS_THAN)) {
                Object rightValue = parseValueOperand();
                return compare(leftValue, rightValue) < 0;
            }
            if (match(ConditionTokenType.LESS_THAN_OR_EQUALS)) {
                Object rightValue = parseValueOperand();
                return compare(leftValue, rightValue) <= 0;
            }
            if (match(ConditionTokenType.IN)) {
                Object rightValue = parseValueOperand();
                return matchesInCollection(leftValue, rightValue, context, input, workflowState);
            }
            if (match(ConditionTokenType.NOT_IN)) {
                Object rightValue = parseValueOperand();
                return !matchesInCollection(leftValue, rightValue, context, input, workflowState);
            }
            return toBooleanValue(leftValue);
        }

        private Object parseValueOperand() {
            if (peek(ConditionTokenType.VALUE)
                    && peekNext(ConditionTokenType.LEFT_PAREN)
                    && isFunctionName(tokens.get(index).value())) {
                return parseFunctionInvocation();
            }
            ConditionToken valueToken = consume(ConditionTokenType.VALUE);
            return resolveConditionValue(valueToken.value(), context, input, workflowState);
        }

        private Object parseFunctionInvocation() {
            String functionName = consume(ConditionTokenType.VALUE).value();
            consume(ConditionTokenType.LEFT_PAREN);
            java.util.List<Object> arguments = new java.util.ArrayList<>();
            if (!peek(ConditionTokenType.RIGHT_PAREN)) {
                arguments.add(parseValueOperand());
                while (match(ConditionTokenType.COMMA)) {
                    arguments.add(parseValueOperand());
                }
            }
            consume(ConditionTokenType.RIGHT_PAREN);
            return evaluateFunction(functionName, arguments);
        }

        private int compare(Object left, Object right) {
            return compareOperands(left, right);
        }

        private boolean match(ConditionTokenType type) {
            if (peek(type)) {
                index++;
                return true;
            }
            return false;
        }

        private boolean peek(ConditionTokenType type) {
            return index < tokens.size() && tokens.get(index).type() == type;
        }

        private boolean peekNext(ConditionTokenType type) {
            return index + 1 < tokens.size() && tokens.get(index + 1).type() == type;
        }

        private ConditionToken consume(ConditionTokenType type) {
            if (!peek(type)) {
                throw new IllegalArgumentException("Unexpected condition token");
            }
            return tokens.get(index++);
        }
    }

    private String stringify(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isValueNode()) {
            return value.asText(value.toString());
        }
        return value.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
