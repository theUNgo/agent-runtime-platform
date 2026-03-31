package com.example.agentruntime.skill;

import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityRegistry;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Iterator;
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
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public WorkflowSkillExecutionSupport(ObjectProvider<CapabilityRegistry> capabilityRegistryProvider,
                                         ObjectMapper objectMapper,
                                         MessageService messageService) {
        this.capabilityRegistryProvider = capabilityRegistryProvider;
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
            if (current == '(') {
                tokens.add(new ConditionToken(ConditionTokenType.LEFT_PAREN, "("));
                index++;
                continue;
            }
            if (current == ')') {
                tokens.add(new ConditionToken(ConditionTokenType.RIGHT_PAREN, ")"));
                index++;
                continue;
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
                if ("&&".equals(pair) || "||".equals(pair) || "==".equals(pair) || "!=".equals(pair)) {
                    break;
                }
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
     * 解析条件表达式的单侧操作数。
     * 支持三类写法：
     * 1. `state.xxx` / `input.xxx` 路径
     * 2. `{{state.xxx}}` 这类模板占位
     * 3. 直接字面量，允许包裹单引号或双引号
     */
    private String resolveConditionOperand(String rawOperand,
                                           CapabilityContext context,
                                           JsonNode input,
                                           ObjectNode workflowState) {
        String operand = rawOperand == null ? "" : rawOperand.trim();
        if (operand.startsWith("{{") && operand.endsWith("}}")) {
            String inner = operand.substring(2, operand.length() - 2).trim();
            return resolveExpression(inner, context, input, workflowState);
        }
        if ((operand.startsWith("\"") && operand.endsWith("\""))
                || (operand.startsWith("'") && operand.endsWith("'"))) {
            return operand.substring(1, operand.length() - 1);
        }
        if ("userMessage".equals(operand)
                || "workspaceRoot".equals(operand)
                || "input".equals(operand)
                || "state".equals(operand)
                || operand.startsWith("input.")
                || operand.startsWith("state.")) {
            return resolveExpression(operand, context, input, workflowState);
        }
        return operand;
    }

    /**
     * 把单个值解释成布尔真值。
     * 这里与 shouldExecute 的兜底规则保持一致，便于条件子表达式复用。
     */
    private boolean toBooleanValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !(normalized.isEmpty()
                || "false".equals(normalized)
                || "0".equals(normalized)
                || "no".equals(normalized)
                || "null".equals(normalized)
                || "off".equals(normalized));
    }

    private record ConditionToken(ConditionTokenType type, String value) {
    }

    private enum ConditionTokenType {
        LEFT_PAREN,
        RIGHT_PAREN,
        AND,
        OR,
        EQUALS,
        NOT_EQUALS,
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

        private boolean parseOr() {
            boolean result = parseAnd();
            while (match(ConditionTokenType.OR)) {
                boolean right = parseAnd();
                result = result || right;
            }
            return result;
        }

        private boolean parseAnd() {
            boolean result = parsePrimary();
            while (match(ConditionTokenType.AND)) {
                boolean right = parsePrimary();
                result = result && right;
            }
            return result;
        }

        private boolean parsePrimary() {
            if (match(ConditionTokenType.LEFT_PAREN)) {
                boolean result = parseOr();
                consume(ConditionTokenType.RIGHT_PAREN);
                return result;
            }
            return parseAtomicCondition();
        }

        private boolean parseAtomicCondition() {
            ConditionToken leftToken = consume(ConditionTokenType.VALUE);
            if (match(ConditionTokenType.EQUALS)) {
                ConditionToken rightToken = consume(ConditionTokenType.VALUE);
                return resolveOperand(leftToken.value()).equals(resolveOperand(rightToken.value()));
            }
            if (match(ConditionTokenType.NOT_EQUALS)) {
                ConditionToken rightToken = consume(ConditionTokenType.VALUE);
                return !resolveOperand(leftToken.value()).equals(resolveOperand(rightToken.value()));
            }
            return toBooleanValue(resolveOperand(leftToken.value()));
        }

        private String resolveOperand(String operand) {
            return resolveConditionOperand(operand, context, input, workflowState);
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
