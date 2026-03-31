<script setup lang="ts">
import { computed } from 'vue'
import type { PreviewExtraBodyTemplate, PreviewTemplateSnippet } from '../lib/preview-templates'

/**
 * 试跑模板编辑区。
 * 负责承接模板选择、系统提示词、few-shot、输出字段说明和扩展参数输入，
 * 让 Dashboard 主页面只保留状态编排与请求发送逻辑。
 */
const props = defineProps<{
  templates: PreviewExtraBodyTemplate[]
  selectedTemplateId: string
  activeTemplateIds: string[]
  customTemplateName: string
  customTemplateDescription: string
  canDeleteSelectedTemplate: boolean
  previewSystemPrompt: string
  previewExamples: string
  previewOutputGuide: string
  previewExtraBody: string
  previewThinkingEnabled: boolean
}>()

const emit = defineEmits<{
  'update:selectedTemplateId': [value: string]
  'update:previewSystemPrompt': [value: string]
  'update:previewExamples': [value: string]
  'update:previewOutputGuide': [value: string]
  'update:previewExtraBody': [value: string]
  'update:previewThinkingEnabled': [value: boolean]
  'update:customTemplateName': [value: string]
  'update:customTemplateDescription': [value: string]
  'apply-template': [payload: { templateId: string, mode: 'replace' | 'merge' }]
  'append-snippet': [snippet: PreviewTemplateSnippet]
  'save-custom-template': []
  'delete-selected-template': []
}>()

const selectedTemplate = computed(() => {
  return props.templates.find((item) => item.id === props.selectedTemplateId) ?? null
})

const activeTemplates = computed(() => {
  return props.templates.filter((item) => props.activeTemplateIds.includes(item.id))
})

function handleTemplateChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  emit('update:selectedTemplateId', value)
}

function applySelectedTemplate(mode: 'replace' | 'merge') {
  if (!props.selectedTemplateId) {
    return
  }
  emit('apply-template', { templateId: props.selectedTemplateId, mode })
}

function appendSnippet(snippet: PreviewTemplateSnippet) {
  emit('append-snippet', snippet)
}
</script>

<template>
  <div class="asset-item">
    <strong>自定义模板</strong>
    <label class="field-card">
      <span>模板名称</span>
      <input
        :value="customTemplateName"
        placeholder="例如：我的 OCR 票据模板"
        @input="emit('update:customTemplateName', ($event.target as HTMLInputElement).value)"
      >
    </label>
    <label class="field-card">
      <span>模板说明</span>
      <input
        :value="customTemplateDescription"
        placeholder="例如：适合发票、收据和海报字段抽取"
        @input="emit('update:customTemplateDescription', ($event.target as HTMLInputElement).value)"
      >
    </label>
    <div class="hero-actions wrap-start">
      <button class="ghost-button small" type="button" @click="emit('save-custom-template')">保存为自定义模板</button>
      <button
        class="ghost-button small"
        type="button"
        :disabled="!canDeleteSelectedTemplate"
        @click="emit('delete-selected-template')"
      >
        删除当前自定义模板
      </button>
    </div>
  </div>

  <label class="field-card">
    <span>系统提示词（可选）</span>
    <textarea
      :value="previewSystemPrompt"
      rows="3"
      placeholder="例如：你是一个严格输出 JSON 的视觉抽取助手。"
      @input="emit('update:previewSystemPrompt', ($event.target as HTMLTextAreaElement).value)"
    ></textarea>
    <small>适合做结构化输出、角色约束和供应商联调时的系统级提示控制。</small>
  </label>

  <label class="field-card">
    <span>Few-shot 示例（可选，JSON 数组）</span>
    <textarea
      :value="previewExamples"
      rows="6"
      placeholder='[{"role":"user","content":"请返回 JSON"},{"role":"assistant","content":"{\"ok\":true}"}]'
      @input="emit('update:previewExamples', ($event.target as HTMLTextAreaElement).value)"
    ></textarea>
    <small>用于预填消息示例，帮助模型更稳定地按目标格式回答。</small>
  </label>

  <label class="field-card">
    <span>输出字段说明（可选）</span>
    <textarea
      :value="previewOutputGuide"
      rows="5"
      placeholder="例如：返回字段包含 title、summary、confidence；如果缺失请返回 null。"
      @input="emit('update:previewOutputGuide', ($event.target as HTMLTextAreaElement).value)"
    ></textarea>
    <small>这部分会自动拼接到系统提示词里，适合结构化抽取和固定字段输出场景。</small>
  </label>

  <label class="field-card">
    <span>额外供应商参数（可选，JSON）</span>
    <select :value="selectedTemplateId" @change="handleTemplateChange">
      <option value="">请选择参数模板</option>
      <option v-for="template in templates" :key="template.id" :value="template.id">
        {{ template.category }} / {{ template.label }}
      </option>
    </select>
    <div class="hero-actions wrap-start">
      <button class="ghost-button small" type="button" :disabled="!selectedTemplateId" @click="applySelectedTemplate('replace')">
        覆盖应用模板
      </button>
      <button class="ghost-button small" type="button" :disabled="!selectedTemplateId" @click="applySelectedTemplate('merge')">
        叠加模板
      </button>
    </div>
    <textarea
      :value="previewExtraBody"
      rows="5"
      placeholder='{"stream": false, "metadata": {"scene": "preview"}}'
      @input="emit('update:previewExtraBody', ($event.target as HTMLTextAreaElement).value)"
    ></textarea>
    <small>这里可以填写 OpenAI-compatible 网关的扩展参数，要求是一个 JSON 对象。</small>
    <div v-if="activeTemplates.length" class="model-capability-line">
      <span v-for="template in activeTemplates" :key="template.id" class="tag-chip">
        已应用：{{ template.label }}
      </span>
    </div>
    <div v-if="selectedTemplate" class="asset-item">
      <strong>{{ selectedTemplate.label }}</strong>
      <span>{{ selectedTemplate.category }} · {{ selectedTemplate.description }}</span>
      <span v-if="selectedTemplate.recommendedSystemPrompt" class="muted-note">
        建议系统提示词：{{ selectedTemplate.recommendedSystemPrompt }}
      </span>
      <span v-if="selectedTemplate.recommendedMessage" class="muted-note">
        建议提示词：{{ selectedTemplate.recommendedMessage }}
      </span>
      <span v-if="selectedTemplate.recommendedExamples?.length" class="item-state">
        已附带 {{ selectedTemplate.recommendedExamples.length }} 条示例消息
      </span>
      <span v-if="selectedTemplate.recommendedOutputGuide" class="muted-note">
        建议字段说明：{{ selectedTemplate.recommendedOutputGuide }}
      </span>
      <span v-if="selectedTemplate.recommendedThinkingEnabled != null" class="item-state">
        建议 thinking：{{ selectedTemplate.recommendedThinkingEnabled ? '开启' : '关闭' }}
      </span>
      <span
        v-for="note in selectedTemplate.recommendedNotes ?? []"
        :key="selectedTemplate.id + note"
        class="muted-note"
      >
        {{ note }}
      </span>
      <div v-if="selectedTemplate.snippets?.length" class="snippet-actions">
        <button
          v-for="snippet in selectedTemplate.snippets"
          :key="snippet.id"
          class="ghost-button small"
          type="button"
          @click="appendSnippet(snippet)"
        >
          {{ snippet.label }}
        </button>
        <span
          v-for="snippet in selectedTemplate.snippets"
          :key="`${snippet.id}-desc`"
          class="muted-note"
        >
          {{ snippet.label }}：{{ snippet.description }}
        </span>
      </div>
    </div>
  </label>

  <label class="preview-toggle">
    <input
      :checked="previewThinkingEnabled"
      type="checkbox"
      @change="emit('update:previewThinkingEnabled', ($event.target as HTMLInputElement).checked)"
    >
    <span>启用 thinking 扩展参数</span>
  </label>
</template>
