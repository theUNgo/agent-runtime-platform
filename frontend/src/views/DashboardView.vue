<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { apiFetch } from '../lib/api'
import { encryptApiKey } from '../lib/crypto'
import PreviewTemplateEditor from '../components/PreviewTemplateEditor.vue'
import { previewExtraBodyTemplates, type PreviewTemplateSnippet } from '../lib/preview-templates'
import {
  clearCustomPreviewTemplates,
  loadCustomPreviewTemplates,
  type CustomPreviewTemplate
} from '../lib/preview-template-storage'

interface Capability {
  id: string
  name: string
  type: string
}

interface CatalogInstallField {
  key: string
  label: string
  placeholder: string
  required: boolean
  secret: boolean
  defaultValue: string
  description: string
}

interface CatalogItem {
  id: string
  name: string
  type: string
  description: string
  tags: string[]
  installed: boolean
  userState: string
  installMode: 'DIRECT' | 'TEMPLATE'
  installHint: string
  transport?: string
  installFields: CatalogInstallField[]
}

interface Installation {
  itemId: string
  itemName: string
  itemType: string
  status: string
  provider: string
  metadataJson?: string
}

interface ConversationSummary {
  conversationId: string
  title: string
  updatedAt: string
  modelProfileId?: number | null
}

interface ConversationMessage {
  id: number
  role: string
  content: string
  payloadJson?: string
  createdAt: string
}

interface ConversationTimelineItem {
  key: string
  kind: 'message' | 'audit'
  createdAt: string
  message?: ConversationMessage
  audit?: AuditItem
}

interface ConversationTimelineRound {
  key: string
  startedAt: string
  title: string
  userMessage?: ConversationMessage
  items: ConversationTimelineItem[]
  auditCount: number
  assistantCount: number
  failedAuditCount: number
  totalDurationMs: number
  capabilityNames: string[]
}

interface McpServerStatus {
  serverName: string
  connectionState: string
  protocolVersion?: string
  message?: string
}

interface McpToolDefinition {
  name: string
  title?: string
  description?: string
}

interface McpValidationDiagnostic {
  code: string
  severity: string
  message: string
  suggestion?: string
}

interface McpValidationReport {
  serverName: string
  reachable: boolean
  initialized: boolean
  protocolVersion?: string
  summary: string
  configurationSummary?: string
  toolCount: number
  resourceCount: number
  promptCount: number
  checks: Record<string, string>
  diagnostics: McpValidationDiagnostic[]
}

interface InstallResult {
  message: string
  generatedSnippet?: string
  nextSteps: string[]
}

interface AgentModelSelection {
  profileId: number
  profileName: string
  providerType: string
  baseUrl: string
  modelId: string
  supportsVision: boolean
  supportsAudio: boolean
}

interface AgentResponse {
  conversationId: string
  userMessage: string
  decision: string
  modelSelection?: AgentModelSelection | null
}

interface AuditItem {
  id: number
  conversationId?: string
  capabilityId: string
  capabilityName: string
  capabilityType: string
  provider?: string
  status: string
  requestJson?: string
  resultJson?: string
  traceJson?: string
  errorMessage?: string
  durationMs?: number
  createdAt: string
}

interface InstallationMetadata {
  serverName?: string
  url?: string
  config?: Record<string, string>
}

interface UserModelProfile {
  id: number
  profileName: string
  providerType: string
  baseUrl: string
  modelId: string
  supportsText: boolean
  supportsVision: boolean
  supportsAudio: boolean
  preferredForGeneralChat: boolean
  preferredForTools: boolean
  preferredForSkills: boolean
  enabled: boolean
  isDefault: boolean
  hasApiKey: boolean
  maskedApiKey: string
  accessStatus: string
  reviewComment?: string | null
}

interface AdminUser {
  userId: number
  username: string
  displayName: string
  role: string
  enabled: boolean
}

interface ModelAccessRequest {
  requestId: number
  userId: number
  username: string
  displayName: string
  profileId: number
  profileName: string
  accessStatus: string
  enabled: boolean
  isDefault: boolean
  reviewComment?: string | null
  requestedAt?: string | null
  reviewedAt?: string | null
  reviewedBy?: string | null
}

interface ModelValidationReport {
  profileId: number
  profileName: string
  providerType: string
  modelId: string
  reachable: boolean
  summary: string
  preview?: string
}

interface AdminOverviewSummary {
  totalUsers: number
  activeUsers: number
  adminUsers: number
  managedMcpServers: number
  managedSkills: number
  managedModels: number
  enabledCatalogSelections: number
  approvedModelSelections: number
  pendingModelApprovals: number
  totalCapabilityInvocations: number
}

interface AdminManagedResourceStat {
  itemId: string
  itemName: string
  itemType: string
  status: string
  provider?: string
  enabledUserCount: number
}

interface AdminModelStat {
  profileId: number
  profileName: string
  providerType: string
  modelId: string
  enabled: boolean
  approvedUserCount: number
  pendingUserCount: number
  defaultUserCount: number
}

interface AdminCapabilityHotspot {
  capabilityId: string
  capabilityName: string
  capabilityType: string
  provider?: string
  invocationCount: number
  successCount: number
  failureCount: number
}

interface AdminInvocationTrendPoint {
  date: string
  invocationCount: number
  successCount: number
  failureCount: number
}

interface AdminResourceDistribution {
  itemType: string
  status: string
  count: number
}

interface AdminModelAccessDistribution {
  accessStatus: string
  count: number
}

interface AdminOverviewStats {
  generatedAt: string
  summary: AdminOverviewSummary
  managedCatalogItems: AdminManagedResourceStat[]
  models: AdminModelStat[]
  topCapabilities: AdminCapabilityHotspot[]
  invocationTrend: AdminInvocationTrendPoint[]
  resourceDistributions: AdminResourceDistribution[]
  modelAccessDistributions: AdminModelAccessDistribution[]
}

interface ModelForm {
  profileName: string
  providerType: string
  baseUrl: string
  apiKey: string
  modelId: string
  supportsText: boolean
  supportsVision: boolean
  supportsAudio: boolean
  preferredForGeneralChat: boolean
  preferredForTools: boolean
  preferredForSkills: boolean
  enabled: boolean
  makeDefault: boolean
}

interface ModelPreviewResponse {
  profileId: number
  profileName: string
  providerType: string
  modelId: string
  requestMessage: string
  content: string
  finishReason?: string
  rawResponse?: unknown
}

interface PreviewTemplateResource {
  templateKey: string
  label: string
  description: string
  category: string
  message?: string | null
  systemPrompt?: string | null
  examplesJson?: string | null
  outputGuide?: string | null
  payloadJson?: string | null
  thinkingEnabled: boolean
  notes: string[]
  createdAt: string
  updatedAt: string
}

interface PreviewUploadAsset {
  name: string
  value: string
  sizeBytes: number
  mimeType: string
  durationSeconds?: number
}

const authStore = useAuthStore()
const searchQuery = ref('')
const capabilities = ref<Capability[]>([])
const catalogItems = ref<CatalogItem[]>([])
const installations = ref<Installation[]>([])
const conversations = ref<ConversationSummary[]>([])
const mcpStatuses = ref<McpServerStatus[]>([])
const conversationMessages = ref<ConversationMessage[]>([])
const auditItems = ref<AuditItem[]>([])
const modelProfiles = ref<UserModelProfile[]>([])
const adminUsers = ref<AdminUser[]>([])
const modelAccessRequests = ref<ModelAccessRequest[]>([])
const adminOverviewStats = ref<AdminOverviewStats | null>(null)
const activeModel = ref<UserModelProfile | null>(null)
const lastUsedModel = ref<AgentModelSelection | null>(null)
const modelValidationReports = ref<Record<number, ModelValidationReport>>({})
const validatingModelProfiles = ref<Record<number, boolean>>({})
const previewProfileId = ref<number | null>(null)
// 当前会话的临时模型覆盖。设置后只影响后续发送的消息，不会修改用户的全局默认模型。
const chatModelProfileId = ref<number | null>(null)
const previewMessage = ref('请简要回答这次试跑任务。')
const previewSystemPrompt = ref('')
const previewExamples = ref('')
const previewOutputGuide = ref('')
const previewImageUrls = ref('')
const previewAudioUrls = ref('')
const previewExtraBody = ref('')
const previewImageUploads = ref<PreviewUploadAsset[]>([])
const previewAudioUploads = ref<PreviewUploadAsset[]>([])
const previewThinkingEnabled = ref(true)
const previewLoading = ref(false)
const previewError = ref('')
const previewResult = ref<ModelPreviewResponse | null>(null)
const selectedPreviewTemplateId = ref('')
const appliedPreviewTemplateIds = ref<string[]>([])
const customPreviewTemplates = ref<CustomPreviewTemplate[]>([])
const customTemplateName = ref('')
const customTemplateDescription = ref('')
const installResult = ref<InstallResult | null>(null)
const validationReports = ref<Record<string, McpValidationReport>>({})
const loading = ref(false)
const sending = ref(false)
const savingModel = ref(false)
const modelError = ref('')
const editingModelId = ref<number | null>(null)
const activeConversationId = ref('')
const chatMessage = ref('')
const installForms = ref<Record<string, Record<string, string>>>({})
const toolMap = ref<Record<string, McpToolDefinition[]>>({})
const expandedServer = ref('')
const savingInstallations = ref<Record<string, boolean>>({})
const validatingServers = ref<Record<string, boolean>>({})
const auditStatusFilter = ref('')
const auditTypeFilter = ref('')
const auditKeyword = ref('')
const expandedAuditId = ref<number | null>(null)
const collapsedTimelineRoundKeys = ref<string[]>([])
const modelForm = ref<ModelForm>(createEmptyModelForm())
const selectedGrantUserId = ref<number | null>(null)
const selectedGrantProfileId = ref<number | null>(null)
const grantMakeDefault = ref(false)
const grantingModelAccess = ref(false)

const filteredCatalog = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return catalogItems.value.filter((item) => {
    const text = [item.id, item.name, item.description, item.transport ?? '', ...(item.tags ?? [])].join(' ').toLowerCase()
    return !keyword || text.includes(keyword)
  })
})

const isAdminUser = computed(() => authStore.user?.admin ?? false)
const currentRoleLabel = computed(() => (isAdminUser.value ? '管理员' : '普通用户'))
const currentRoleDescription = computed(() => (
  isAdminUser.value
    ? '你当前负责维护平台的全局 MCP、Skill 和模型档案，普通用户将在这些全局资源上按需启用。'
    : '你当前使用的是平台已经开放的全局资源，可以按需启用能力、选择模型并编排自己的对话工作流。'
))

const availablePreviewTemplates = computed(() => {
  return [...previewExtraBodyTemplates, ...customPreviewTemplates.value]
})

const selectedChatModel = computed(() => {
  if (chatModelProfileId.value == null) {
    return null
  }
  return modelProfiles.value.find((item) => item.id === chatModelProfileId.value) ?? null
})

const currentExecutionModel = computed(() => {
  if (selectedChatModel.value) {
    return toSelection(selectedChatModel.value)
  }
  return lastUsedModel.value ?? toSelection(activeModel.value)
})

const pendingModelAccessRequests = computed(() =>
  modelAccessRequests.value.filter((item) => item.accessStatus === 'PENDING')
)

const previewUploadWarnings = computed(() => {
  const warnings: string[] = []
  const imageCount = previewImageUploads.value.length
  const audioCount = previewAudioUploads.value.length
  const totalBytes = [...previewImageUploads.value, ...previewAudioUploads.value]
    .reduce((sum, item) => sum + item.sizeBytes, 0)

  if (imageCount + audioCount > 6) {
    warnings.push('当前试跑已选择较多本地文件，建议控制在 6 个以内，避免请求体过大。')
  }
  if (totalBytes > 15 * 1024 * 1024) {
    warnings.push('当前本地文件总大小已超过 15 MB，部分模型网关可能会拒绝该请求。')
  }
  if (previewAudioUploads.value.some((item) => (item.durationSeconds ?? 0) > 180)) {
    warnings.push('存在超过 3 分钟的音频文件，建议先裁剪后再试跑。')
  }
  return warnings
})

const activeConversationTitle = computed(() => {
  return conversations.value.find((item) => item.conversationId === activeConversationId.value)?.title ?? '新会话'
})

const auditHeading = computed(() => {
  return activeConversationId.value ? '当前会话的能力调用' : '最近能力调用'
})

/**
 * 管理员趋势卡片需要知道最大值，才能把每天的调用量映射成可比较的条形宽度。
 */
const adminTrendPeak = computed(() => {
  return Math.max(1, ...(adminOverviewStats.value?.invocationTrend ?? []).map((item) => item.invocationCount))
})

/**
 * 会话内更适合按时间顺序查看完整执行轨迹，全局列表则继续保留“最新在前”。
 */
const auditDisplayItems = computed(() => {
  if (!activeConversationId.value) {
    return auditItems.value
  }
  return [...auditItems.value].sort((left, right) => {
    return new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
  })
})

/**
 * 将会话消息和能力调用合并成一条完整时间线。
 * 这样在排查一轮对话时，用户可以直接看到“发消息 -> 调能力 -> 回消息”的顺序。
 */
const conversationTimelineItems = computed<ConversationTimelineItem[]>(() => {
  if (!activeConversationId.value) {
    return []
  }
  const messageItems = conversationMessages.value.map((message) => ({
    key: `message-${message.id}`,
    kind: 'message' as const,
    createdAt: message.createdAt,
    message
  }))
  const auditTimelineItems = auditDisplayItems.value.map((audit) => ({
    key: `audit-${audit.id}`,
    kind: 'audit' as const,
    createdAt: audit.createdAt,
    audit
  }))
  return [...messageItems, ...auditTimelineItems].sort((left, right) => {
    const timeDiff = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
    if (timeDiff !== 0) {
      return timeDiff
    }
    return left.key.localeCompare(right.key)
  })
})

/**
 * 将完整时间线按“用户消息轮次”分组。
 * 一条用户消息以及随后的能力调用、助手回复会被折成同一个可展开区块。
 */
const conversationTimelineRounds = computed<ConversationTimelineRound[]>(() => {
  if (!activeConversationId.value) {
    return []
  }
  const rounds: ConversationTimelineRound[] = []
  let currentRound: ConversationTimelineRound | null = null

  for (const item of conversationTimelineItems.value) {
    const isUserMessage = item.kind === 'message' && item.message?.role?.toUpperCase() === 'USER'
    if (isUserMessage) {
      currentRound = {
        key: `round-${item.message?.id ?? item.key}`,
        startedAt: item.createdAt,
        title: timelineMessagePreview(item.message!),
        userMessage: item.message,
        items: [item],
        auditCount: 0,
        assistantCount: 0,
        failedAuditCount: 0,
        totalDurationMs: 0,
        capabilityNames: []
      }
      rounds.push(currentRound)
      continue
    }

    if (!currentRound) {
      currentRound = {
        key: 'round-bootstrap',
        startedAt: item.createdAt,
        title: '会话开始前的系统轨迹',
        items: [],
        auditCount: 0,
        assistantCount: 0,
        failedAuditCount: 0,
        totalDurationMs: 0,
        capabilityNames: []
      }
      rounds.push(currentRound)
    }

    currentRound.items.push(item)
    if (item.kind === 'audit') {
      currentRound.auditCount += 1
      if (item.audit?.status?.toUpperCase() === 'FAILED') {
        currentRound.failedAuditCount += 1
      }
      if (item.audit?.durationMs != null) {
        currentRound.totalDurationMs += item.audit.durationMs
      }
      if (item.audit?.capabilityName && !currentRound.capabilityNames.includes(item.audit.capabilityName)) {
        currentRound.capabilityNames.push(item.audit.capabilityName)
      }
    } else if (item.message?.role?.toUpperCase() === 'ASSISTANT') {
      currentRound.assistantCount += 1
    }
  }

  return rounds
})

function createEmptyModelForm(): ModelForm {
  return {
    profileName: '',
    providerType: 'openai-compatible',
    baseUrl: '',
    apiKey: '',
    modelId: '',
    supportsText: true,
    supportsVision: false,
    supportsAudio: false,
    preferredForGeneralChat: true,
    preferredForTools: true,
    preferredForSkills: true,
    enabled: true,
    makeDefault: true
  }
}

function toSelection(profile: UserModelProfile | null): AgentModelSelection | null {
  if (!profile) {
    return null
  }
  return {
    profileId: profile.id,
    profileName: profile.profileName,
    providerType: profile.providerType,
    baseUrl: profile.baseUrl,
    modelId: profile.modelId,
    supportsVision: profile.supportsVision,
    supportsAudio: profile.supportsAudio
  }
}

function parseInstallationMetadata(item: Installation): InstallationMetadata {
  if (!item.metadataJson) {
    return {}
  }
  try {
    return JSON.parse(item.metadataJson) as InstallationMetadata
  } catch {
    return {}
  }
}

/**
 * 将趋势值转换成前端条形宽度。
 * 这里保留一个最小宽度，避免数据很小时完全看不见。
 */
function adminBarWidth(value: number, max: number) {
  if (max <= 0) {
    return '8%'
  }
  return `${Math.max(8, Math.round((value / max) * 100))}%`
}

/**
 * 资源分布卡片里优先展示更贴近日常使用的中文标签。
 * 当前只做轻量映射，未知状态则直接回退原始值。
 */
function adminStatusLabel(value: string) {
  const upper = value?.toUpperCase?.() ?? ''
  if (upper === 'ENABLED') {
    return '已启用'
  }
  if (upper === 'DISABLED') {
    return '已禁用'
  }
  if (upper === 'PENDING') {
    return '待审批'
  }
  if (upper === 'APPROVED') {
    return '已批准'
  }
  if (upper === 'REJECTED') {
    return '已拒绝'
  }
  if (upper === 'NOT_REQUESTED') {
    return '未申请'
  }
  return value || '未知'
}

/**
 * 将多行 URL 输入解析为数组，便于试跑面板支持多图和多音频。
 */
function parseUrlLines(value: string) {
  return value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
}

/**
 * 解析额外供应商参数 JSON。
 * 这里仅做前端快速校验，真正的字段透传仍由后端统一处理。
 */
function parseExtraBody(value: string) {
  if (!value.trim()) {
    return null
  }
  const parsed = JSON.parse(value)
  if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('额外参数必须是一个 JSON 对象。')
  }
  return parsed
}

/**
 * 解析 few-shot 示例 JSON。
 * 这里要求传入数组，数组项至少包含 role 和 content，便于后端直接拼到 messages 中。
 */
function parsePreviewExamples(value: string) {
  if (!value.trim()) {
    return null
  }
  const parsed = JSON.parse(value)
  if (!Array.isArray(parsed)) {
    throw new Error('Few-shot 示例必须是一个 JSON 数组。')
  }
  for (const item of parsed) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      throw new Error('Few-shot 示例中的每一项都必须是一个对象。')
    }
    if (typeof item.role !== 'string' || !item.role.trim() || item.content == null) {
      throw new Error('Few-shot 示例中的每一项都必须包含非空的 role 和 content。')
    }
  }
  return parsed
}

/**
 * 拼接最终发给后端的系统提示词。
 * 输出字段说明先在前端收口成附加指令，避免频繁扩大后端请求模型。
 */
function buildPreviewSystemPrompt() {
  const blocks = [previewSystemPrompt.value.trim(), previewOutputGuide.value.trim()].filter(Boolean)
  return blocks.length ? blocks.join('\n\n') : null
}

function currentTemplateStorageKey() {
  return authStore.user?.username || (authStore.user?.userId != null ? String(authStore.user.userId) : '')
}

function mapPreviewTemplateResource(resource: PreviewTemplateResource): CustomPreviewTemplate {
  return {
    id: resource.templateKey,
    label: resource.label,
    category: resource.category,
    description: resource.description,
    payload: parseExtraBody(resource.payloadJson ?? '') ?? {},
    recommendedSystemPrompt: resource.systemPrompt ?? undefined,
    recommendedMessage: resource.message ?? undefined,
    recommendedExamples: parsePreviewExamples(resource.examplesJson ?? '') ?? undefined,
    recommendedOutputGuide: resource.outputGuide ?? undefined,
    recommendedThinkingEnabled: resource.thinkingEnabled,
    recommendedNotes: resource.notes,
    snippets: [],
    source: 'custom',
    createdAt: resource.createdAt,
    updatedAt: resource.updatedAt
  }
}

/**
 * 将模板片段追加到当前编辑内容中。
 * 文本片段走换段拼接，few-shot 片段则按 JSON 数组合并，避免覆盖用户已调好的内容。
 */
function appendPreviewSnippet(snippet: PreviewTemplateSnippet) {
  if (snippet.target === 'systemPrompt') {
    previewSystemPrompt.value = appendTextBlock(previewSystemPrompt.value, snippet.content)
    return
  }
  if (snippet.target === 'outputGuide') {
    previewOutputGuide.value = appendTextBlock(previewOutputGuide.value, snippet.content)
    return
  }
  previewExamples.value = appendExampleSnippet(previewExamples.value, snippet.content)
}

function appendTextBlock(current: string, addition: string) {
  const base = current.trim()
  const next = addition.trim()
  if (!next) {
    return current
  }
  if (!base) {
    return next
  }
  if (base.includes(next)) {
    return current
  }
  return `${base}\n\n${next}`
}

function appendExampleSnippet(current: string, addition: string) {
  const merged: Array<Record<string, unknown>> = []
  const currentValue = current.trim()
  if (currentValue) {
    const parsedCurrent = parsePreviewExamples(currentValue)
    if (parsedCurrent) {
      merged.push(...parsedCurrent)
    }
  }
  const parsedAddition = parsePreviewExamples(addition)
  if (parsedAddition) {
    merged.push(...parsedAddition)
  }
  return merged.length ? JSON.stringify(merged, null, 2) : ''
}

/**
 * 合并模板扩展参数。
 * 这里优先做对象级深合并；当字段冲突时保留后应用模板的值，方便用户显式叠加新模板。
 */
function mergeExtraBodyText(current: string, addition: Record<string, unknown>) {
  const currentText = current.trim()
  const currentObject = currentText ? parseExtraBody(currentText) ?? {} : {}
  const merged = deepMergeObjects(currentObject as Record<string, unknown>, addition)
  return Object.keys(merged).length ? JSON.stringify(merged, null, 2) : ''
}

function deepMergeObjects(base: Record<string, unknown>, addition: Record<string, unknown>) {
  const result: Record<string, unknown> = { ...base }
  for (const [key, value] of Object.entries(addition)) {
    const currentValue = result[key]
    if (isPlainObject(currentValue) && isPlainObject(value)) {
      result[key] = deepMergeObjects(
        currentValue as Record<string, unknown>,
        value as Record<string, unknown>
      )
      continue
    }
    result[key] = value
  }
  return result
}

function isPlainObject(value: unknown) {
  return typeof value === 'object' && value != null && !Array.isArray(value)
}

/**
 * 将字节数格式化成更适合界面展示的大小字符串。
 */
function formatFileSize(sizeBytes: number) {
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`
  }
  return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * 将秒数格式化成 mm:ss，方便展示音频时长。
 */
function formatDuration(seconds?: number) {
  if (seconds == null || Number.isNaN(seconds)) {
    return '未知时长'
  }
  const totalSeconds = Math.max(0, Math.round(seconds))
  const minutes = Math.floor(totalSeconds / 60)
  const remainSeconds = totalSeconds % 60
  return `${minutes}:${String(remainSeconds).padStart(2, '0')}`
}

/**
 * 读取音频元数据时长。
 */
function readAudioDuration(file: File) {
  return new Promise<number | undefined>((resolve) => {
    const objectUrl = URL.createObjectURL(file)
    const audio = document.createElement('audio')
    audio.preload = 'metadata'
    audio.onloadedmetadata = () => {
      URL.revokeObjectURL(objectUrl)
      resolve(audio.duration)
    }
    audio.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      resolve(undefined)
    }
    audio.src = objectUrl
  })
}

/**
 * 将本地文件读取为 data URL，便于直接透传给模型试跑接口。
 */
function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(new Error(`读取文件失败：${file.name}`))
    reader.readAsDataURL(file)
  })
}

/**
 * 将文件选择结果转换成试跑资产列表。
 */
async function buildPreviewAssets(files: FileList | null, kind: 'image' | 'audio') {
  if (!files || files.length === 0) {
    return []
  }
  const result: PreviewUploadAsset[] = []
  for (const file of Array.from(files)) {
    const durationSeconds = kind === 'audio' ? await readAudioDuration(file) : undefined
    result.push({
      name: file.name,
      value: await readFileAsDataUrl(file),
      sizeBytes: file.size,
      mimeType: file.type || 'application/octet-stream',
      durationSeconds
    })
  }
  return result
}

/**
 * 处理图片文件选择。
 */
async function handlePreviewImageFileChange(event: Event) {
  try {
    const input = event.target as HTMLInputElement
    previewImageUploads.value = await buildPreviewAssets(input.files, 'image')
  } catch (error) {
    previewError.value = error instanceof Error ? error.message : '读取图片文件失败。'
  }
}

/**
 * 处理音频文件选择。
 */
async function handlePreviewAudioFileChange(event: Event) {
  try {
    const input = event.target as HTMLInputElement
    previewAudioUploads.value = await buildPreviewAssets(input.files, 'audio')
  } catch (error) {
    previewError.value = error instanceof Error ? error.message : '读取音频文件失败。'
  }
}

/**
 * 应用常见供应商扩展参数模板，减少联调时手写 JSON 的成本。
 */
function applyPreviewTemplate(payload: { templateId: string, mode: 'replace' | 'merge' }) {
  const { templateId, mode } = payload
  selectedPreviewTemplateId.value = templateId
  const template = availablePreviewTemplates.value.find((item) => item.id === templateId)
  if (!template) {
    previewSystemPrompt.value = ''
    previewExamples.value = ''
    previewOutputGuide.value = ''
    previewExtraBody.value = ''
    appliedPreviewTemplateIds.value = []
    return
  }
  if (mode === 'replace') {
    previewExtraBody.value = JSON.stringify(template.payload, null, 2)
    previewSystemPrompt.value = template.recommendedSystemPrompt ?? ''
    previewExamples.value = template.recommendedExamples ? JSON.stringify(template.recommendedExamples, null, 2) : ''
    previewOutputGuide.value = template.recommendedOutputGuide ?? ''
    appliedPreviewTemplateIds.value = [templateId]
    if (template.recommendedMessage) {
      previewMessage.value = template.recommendedMessage
    }
    if (template.recommendedThinkingEnabled != null) {
      previewThinkingEnabled.value = template.recommendedThinkingEnabled
    }
    if ('source' in template && template.source === 'custom') {
      customTemplateName.value = template.label
      customTemplateDescription.value = template.description
    }
    return
  }

  previewExtraBody.value = mergeExtraBodyText(previewExtraBody.value, template.payload)
  if (template.recommendedSystemPrompt) {
    previewSystemPrompt.value = appendTextBlock(previewSystemPrompt.value, template.recommendedSystemPrompt)
  }
  if (template.recommendedExamples?.length) {
    previewExamples.value = appendExampleSnippet(
      previewExamples.value,
      JSON.stringify(template.recommendedExamples, null, 2)
    )
  }
  if (template.recommendedOutputGuide) {
    previewOutputGuide.value = appendTextBlock(previewOutputGuide.value, template.recommendedOutputGuide)
  }
  if (template.recommendedMessage) {
    const defaultMessage = '请简要回答这次试跑任务。'
    previewMessage.value = !previewMessage.value.trim() || previewMessage.value.trim() === defaultMessage
      ? template.recommendedMessage
      : appendTextBlock(previewMessage.value, `补充要求：${template.recommendedMessage}`)
  }
  if (template.recommendedThinkingEnabled) {
    previewThinkingEnabled.value = template.recommendedThinkingEnabled
  }
  if (!appliedPreviewTemplateIds.value.includes(templateId)) {
    appliedPreviewTemplateIds.value = [...appliedPreviewTemplateIds.value, templateId]
  }
  if ('source' in template && template.source === 'custom') {
    customTemplateName.value = template.label
    customTemplateDescription.value = template.description
  }
}

/**
 * 从当前试跑配置生成一个用户自定义模板。
 * 当前版本先保存在浏览器本地，并按登录用户隔离。
 */
async function saveCurrentAsCustomTemplate() {
  try {
    const name = customTemplateName.value.trim()
    if (!name) {
      previewError.value = '请先填写自定义模板名称。'
      return
    }
    const userKey = currentTemplateStorageKey()
    if (!userKey) {
      previewError.value = '当前用户信息不可用，暂时无法保存自定义模板。'
      return
    }

    const existing = customPreviewTemplates.value.find((item) => item.id === selectedPreviewTemplateId.value && item.source === 'custom')
    const now = new Date().toISOString()
    const template: CustomPreviewTemplate = {
      id: existing?.id ?? `custom-${Date.now()}`,
      label: name,
      category: '我的模板',
      description: customTemplateDescription.value.trim() || '用户自定义试跑模板',
      payload: parseExtraBody(previewExtraBody.value) ?? {},
      recommendedSystemPrompt: previewSystemPrompt.value.trim() || undefined,
      recommendedMessage: previewMessage.value.trim() || undefined,
      recommendedExamples: parsePreviewExamples(previewExamples.value) ?? undefined,
      recommendedOutputGuide: previewOutputGuide.value.trim() || undefined,
      recommendedThinkingEnabled: previewThinkingEnabled.value,
      recommendedNotes: appliedPreviewTemplateIds.value.length
        ? [`组合来源：${appliedPreviewTemplateIds.value.join('、')}`]
        : ['来源：当前手工配置'],
      snippets: [],
      source: 'custom',
      createdAt: existing?.createdAt ?? now,
      updatedAt: now
    }

    await apiFetch<PreviewTemplateResource>(`/api/me/preview-templates/${encodeURIComponent(template.id)}`, {
      method: 'PUT',
      body: JSON.stringify({
        label: template.label,
        description: template.description,
        message: template.recommendedMessage ?? null,
        systemPrompt: template.recommendedSystemPrompt ?? null,
        examplesJson: template.recommendedExamples ? JSON.stringify(template.recommendedExamples, null, 2) : null,
        outputGuide: template.recommendedOutputGuide ?? null,
        payloadJson: Object.keys(template.payload).length ? JSON.stringify(template.payload, null, 2) : null,
        thinkingEnabled: template.recommendedThinkingEnabled ?? false,
        notes: template.recommendedNotes ?? []
      })
    }, authStore.token)

    await loadCustomTemplates()

    selectedPreviewTemplateId.value = template.id
    if (!appliedPreviewTemplateIds.value.includes(template.id)) {
      appliedPreviewTemplateIds.value = [...appliedPreviewTemplateIds.value, template.id]
    }
    previewError.value = ''
  } catch (error) {
    previewError.value = error instanceof Error ? error.message : '保存自定义模板失败。'
  }
}

async function deleteSelectedCustomTemplate() {
  const templateId = selectedPreviewTemplateId.value
  if (!templateId) {
    return
  }
  const current = customPreviewTemplates.value.find((item) => item.id === templateId)
  if (!current) {
    previewError.value = '当前选中的模板不是自定义模板，无法删除。'
    return
  }
  const userKey = currentTemplateStorageKey()
  if (!userKey) {
    previewError.value = '当前用户信息不可用，暂时无法删除自定义模板。'
    return
  }
  try {
    await apiFetch<void>(`/api/me/preview-templates/${encodeURIComponent(templateId)}`, {
      method: 'DELETE'
    }, authStore.token)
    await loadCustomTemplates()
    selectedPreviewTemplateId.value = ''
    appliedPreviewTemplateIds.value = appliedPreviewTemplateIds.value.filter((item) => item !== templateId)
    previewError.value = ''
  } catch (error) {
    previewError.value = error instanceof Error ? error.message : '删除自定义模板失败。'
  }
}

function ensureInstallForms(items: CatalogItem[], installed: Installation[]) {
  const next = { ...installForms.value }
  for (const item of items) {
    const current = next[item.id] ?? {}
    const merged: Record<string, string> = { ...current }
    const installedItem = installed.find((candidate) => candidate.itemType === 'MCP_SERVER' && candidate.itemId === item.id)
    const metadata = installedItem ? parseInstallationMetadata(installedItem) : {}
    const metadataConfig = metadata.config ?? {}
    for (const field of item.installFields ?? []) {
      const metadataValue = field.key === 'serverName'
        ? (metadata.serverName ?? metadataConfig[field.key])
        : metadataConfig[field.key]
      merged[field.key] = metadataValue ?? current[field.key] ?? field.defaultValue ?? ''
    }
    next[item.id] = merged
  }
  installForms.value = next
}

function catalogById(itemId: string) {
  return catalogItems.value.find((item) => item.id === itemId)
}

function installationMetadataSummary(item: Installation) {
  const metadata = parseInstallationMetadata(item)
  const parts = [metadata.serverName, metadata.url, metadata.config?.rootPath].filter(Boolean)
  return parts.join(' | ')
}

function installedServerName(item: Installation) {
  const metadata = parseInstallationMetadata(item)
  return metadata.serverName || item.itemName
}

function beginCreateModel() {
  editingModelId.value = null
  modelError.value = ''
  modelForm.value = createEmptyModelForm()
}

function beginEditModel(profile: UserModelProfile) {
  editingModelId.value = profile.id
  modelError.value = ''
  modelForm.value = {
    profileName: profile.profileName,
    providerType: profile.providerType,
    baseUrl: profile.baseUrl,
    apiKey: '',
    modelId: profile.modelId,
    supportsText: profile.supportsText,
    supportsVision: profile.supportsVision,
    supportsAudio: profile.supportsAudio,
    preferredForGeneralChat: profile.preferredForGeneralChat,
    preferredForTools: profile.preferredForTools,
    preferredForSkills: profile.preferredForSkills,
    enabled: profile.enabled,
    makeDefault: profile.isDefault
  }
}

function modelCapabilitySummary(profile: Pick<UserModelProfile, 'supportsText' | 'supportsVision' | 'supportsAudio'>) {
  const values = []
  if (profile.supportsText) values.push('文本')
  if (profile.supportsVision) values.push('视觉')
  if (profile.supportsAudio) values.push('音频')
  return values.length ? values.join(' / ') : '未声明能力'
}

function modelPreferenceSummary(profile: Pick<UserModelProfile, 'preferredForGeneralChat' | 'preferredForTools' | 'preferredForSkills'>) {
  const values = []
  if (profile.preferredForGeneralChat) values.push('通用对话')
  if (profile.preferredForTools) values.push('工具调用')
  if (profile.preferredForSkills) values.push('Skill 执行')
  return values.length ? values.join(' / ') : '未声明偏好'
}

/**
 * 切换单条审计记录的详情展开状态。
 */
function toggleAuditItem(itemId: number) {
  expandedAuditId.value = expandedAuditId.value === itemId ? null : itemId
}

/**
 * 将审计里的 JSON 字符串安全格式化，便于直接在页面中查看请求和结果。
 */
function prettyAuditJson(value?: string) {
  if (!value) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function auditStatusLabel(status: string) {
  const normalized = (status || '').toUpperCase()
  if (normalized === 'SUCCESS') return '成功'
  if (normalized === 'FAILED') return '失败'
  if (normalized === 'STARTED') return '执行中'
  return status || '未知'
}

function auditStatusClass(status: string) {
  const normalized = (status || '').toUpperCase()
  if (normalized === 'SUCCESS') return 'audit-status-success'
  if (normalized === 'FAILED') return 'audit-status-failed'
  if (normalized === 'STARTED') return 'audit-status-running'
  return 'audit-status-default'
}

function auditPreview(item: AuditItem) {
  if (item.errorMessage) {
    return item.errorMessage
  }
  if (item.resultJson) {
    const compact = prettyAuditJson(item.resultJson).replace(/\s+/g, ' ').trim()
    return compact.length > 120 ? `${compact.slice(0, 120)}...` : compact
  }
  return '暂无结果摘要'
}

/**
 * 将耗时格式化为更易读的文本。
 */
function auditDurationLabel(durationMs?: number) {
  if (durationMs == null) {
    return ''
  }
  if (durationMs < 1000) {
    return `${durationMs} ms`
  }
  return `${(durationMs / 1000).toFixed(durationMs >= 10_000 ? 0 : 1)} s`
}

function conversationMessageRoleLabel(role: string) {
  const normalized = (role || '').toUpperCase()
  if (normalized === 'USER') return '用户消息'
  if (normalized === 'ASSISTANT') return '助手回复'
  return role || '消息'
}

function conversationMessageRoleClass(role: string) {
  const normalized = (role || '').toUpperCase()
  if (normalized === 'USER') return 'timeline-chip-user'
  if (normalized === 'ASSISTANT') return 'timeline-chip-assistant'
  return 'timeline-chip-default'
}

function timelineMessagePreview(message: ConversationMessage) {
  const compact = (message.content || '').replace(/\s+/g, ' ').trim()
  if (!compact) {
    return '空消息'
  }
  return compact.length > 120 ? `${compact.slice(0, 120)}...` : compact
}

function timelineAuditTitle(item: AuditItem) {
  return `${item.capabilityName} (${item.capabilityType})`
}

function isTimelineRoundCollapsed(roundKey: string) {
  return collapsedTimelineRoundKeys.value.includes(roundKey)
}

/**
 * 切换单个会话轮次的展开状态。
 */
function toggleTimelineRound(roundKey: string) {
  if (isTimelineRoundCollapsed(roundKey)) {
    collapsedTimelineRoundKeys.value = collapsedTimelineRoundKeys.value.filter((key) => key !== roundKey)
    return
  }
  collapsedTimelineRoundKeys.value = [...collapsedTimelineRoundKeys.value, roundKey]
}

function timelineRoundSummary(round: ConversationTimelineRound) {
  const values: string[] = []
  if (round.auditCount) {
    values.push(`${round.auditCount} 次能力调用`)
  }
  if (round.assistantCount) {
    values.push(`${round.assistantCount} 条助手回复`)
  }
  return values.length ? values.join(' / ') : '仅包含用户消息'
}

/**
 * 生成轮次级摘要标签。
 * 这里优先展示失败数、总耗时和能力名称，帮助用户在不展开的情况下快速定位关键轮次。
 */
function timelineRoundChips(round: ConversationTimelineRound) {
  const chips: string[] = []
  if (round.failedAuditCount > 0) {
    chips.push(`失败 ${round.failedAuditCount} 次`)
  } else if (round.auditCount > 0) {
    chips.push('本轮能力调用成功')
  }
  if (round.totalDurationMs > 0) {
    chips.push(`累计耗时：${auditDurationLabel(round.totalDurationMs)}`)
  }
  if (round.capabilityNames.length) {
    const label = round.capabilityNames.slice(0, 2).join(' / ')
    chips.push(round.capabilityNames.length > 2 ? `能力：${label} 等` : `能力：${label}`)
  }
  return chips
}

/**
 * 在会话视角下生成顺序标签，帮助用户把多次能力调用串成完整时间线。
 */
function auditTimelineStep(itemId: number) {
  const index = auditDisplayItems.value.findIndex((item) => item.id === itemId)
  return index >= 0 ? `步骤 ${index + 1}` : ''
}

function auditSummaryChips(item: AuditItem) {
  const chips: string[] = []
  const request = safeParseJson(item.requestJson)
  const result = safeParseJson(item.resultJson)
  const trace = safeParseJson(item.traceJson) as Record<string, unknown> | null

  const requestKeys = request && typeof request === 'object' && !Array.isArray(request)
    ? Object.keys(request as Record<string, unknown>).slice(0, 3)
    : []
  if (requestKeys.length) {
    chips.push(`请求字段：${requestKeys.join(' / ')}`)
  }

  if (result && typeof result === 'object') {
    if (Array.isArray(result)) {
      chips.push(`结果数组：${result.length} 项`)
    } else {
      const resultKeys = Object.keys(result as Record<string, unknown>).slice(0, 3)
      if (resultKeys.length) {
        chips.push(`结果字段：${resultKeys.join(' / ')}`)
      }
    }
  }

  const errorCategory = auditErrorCategory(item.errorMessage)
  if (errorCategory) {
    chips.push(`失败原因：${errorCategory}`)
  }

  const durationLabel = auditDurationLabel(item.durationMs)
  if (durationLabel) {
    chips.push(`耗时：${durationLabel}`)
  }

  const model = trace && typeof trace.model === 'object' && trace.model != null
    ? trace.model as Record<string, unknown>
    : null
  if (model?.modelId) {
    chips.push(`模型：${String(model.modelId)}`)
  }
  if (typeof trace?.assistantMessageSource === 'string') {
    chips.push(`回复来源：${String(trace.assistantMessageSource)}`)
  }

  return chips
}

/**
 * 提取阶段耗时文本。
 * 这里只展示最重要的 capability / model / total 三段，避免把细节直接塞进主摘要。
 */
function auditPhaseDurations(item: AuditItem) {
  const trace = safeParseJson(item.traceJson) as Record<string, unknown> | null
  const phaseDurations = trace && typeof trace.phaseDurations === 'object' && trace.phaseDurations != null
    ? trace.phaseDurations as Record<string, unknown>
    : null
  if (!phaseDurations) {
    return []
  }
  const result: string[] = []
  if (typeof phaseDurations.capabilityDurationMs === 'number') {
    result.push(`能力执行：${auditDurationLabel(phaseDurations.capabilityDurationMs)}`)
  }
  if (typeof phaseDurations.modelDurationMs === 'number') {
    result.push(`模型整理：${auditDurationLabel(phaseDurations.modelDurationMs)}`)
  }
  if (typeof phaseDurations.totalDurationMs === 'number') {
    result.push(`总耗时：${auditDurationLabel(phaseDurations.totalDurationMs)}`)
  }
  return result
}

function auditErrorCategory(errorMessage?: string) {
  const text = (errorMessage || '').toLowerCase()
  if (!text) {
    return ''
  }
  if (text.includes('timeout') || text.includes('超时')) return '超时'
  if (text.includes('auth') || text.includes('token') || text.includes('鉴权')) return '鉴权'
  if (text.includes('not found') || text.includes('未找到')) return '未找到'
  if (text.includes('validation') || text.includes('校验')) return '参数校验'
  if (text.includes('network') || text.includes('connect') || text.includes('连接')) return '连接异常'
  return '执行失败'
}

function safeParseJson(value?: string) {
  if (!value) {
    return null
  }
  try {
    return JSON.parse(value) as unknown
  } catch {
    return null
  }
}

function auditTraceModelLabel(item: AuditItem) {
  const trace = safeParseJson(item.traceJson) as Record<string, unknown> | null
  if (!trace || typeof trace !== 'object' || !trace.model || typeof trace.model !== 'object') {
    return ''
  }
  const model = trace.model as Record<string, unknown>
  const profileName = typeof model.profileName === 'string' ? model.profileName : ''
  const modelId = typeof model.modelId === 'string' ? model.modelId : ''
  return [profileName, modelId].filter(Boolean).join(' / ')
}

async function loadModels() {
  const [profiles, active] = await Promise.all([
    apiFetch<UserModelProfile[]>('/api/me/models', {}, authStore.token),
    apiFetch<UserModelProfile | null>('/api/me/models/active', {}, authStore.token)
  ])
  modelProfiles.value = profiles
  activeModel.value = active
  if (profiles.length === 0) {
    previewProfileId.value = null
    chatModelProfileId.value = null
    return
  }
  if (previewProfileId.value == null || !profiles.some((item) => item.id === previewProfileId.value)) {
    previewProfileId.value = active?.id ?? profiles[0].id
  }
  if (chatModelProfileId.value != null && !profiles.some((item) => item.id === chatModelProfileId.value)) {
    chatModelProfileId.value = null
  }
}

async function loadAdminModelAccess() {
  if (!isAdminUser.value) {
    adminUsers.value = []
    modelAccessRequests.value = []
    return
  }
  const [users, requests] = await Promise.all([
    apiFetch<AdminUser[]>('/api/admin/users', {}, authStore.token),
    apiFetch<ModelAccessRequest[]>('/api/admin/model-access/requests?status=PENDING', {}, authStore.token)
  ])
  adminUsers.value = users.filter((item) => !item.role?.includes('ADMIN'))
  modelAccessRequests.value = requests
  if (selectedGrantUserId.value == null && adminUsers.value.length > 0) {
    selectedGrantUserId.value = adminUsers.value[0].userId
  }
  if (selectedGrantProfileId.value == null && modelProfiles.value.length > 0) {
    selectedGrantProfileId.value = modelProfiles.value[0].id
  }
}

async function loadAdminOverviewStats() {
  if (!isAdminUser.value) {
    adminOverviewStats.value = null
    return
  }
  adminOverviewStats.value = await apiFetch<AdminOverviewStats>('/api/admin/overview', {}, authStore.token)
}

async function loadCustomTemplates() {
  const userKey = currentTemplateStorageKey()
  if (!userKey) {
    customPreviewTemplates.value = []
    return
  }
  const localTemplates = loadCustomPreviewTemplates(userKey)
  let remoteTemplates = await apiFetch<PreviewTemplateResource[]>('/api/me/preview-templates', {}, authStore.token)

  if (localTemplates.length > 0) {
    for (const template of localTemplates) {
      await apiFetch<PreviewTemplateResource>(`/api/me/preview-templates/${encodeURIComponent(template.id)}`, {
        method: 'PUT',
        body: JSON.stringify({
          label: template.label,
          description: template.description,
          message: template.recommendedMessage ?? null,
          systemPrompt: template.recommendedSystemPrompt ?? null,
          examplesJson: template.recommendedExamples ? JSON.stringify(template.recommendedExamples, null, 2) : null,
          outputGuide: template.recommendedOutputGuide ?? null,
          payloadJson: Object.keys(template.payload).length ? JSON.stringify(template.payload, null, 2) : null,
          thinkingEnabled: template.recommendedThinkingEnabled ?? false,
          notes: template.recommendedNotes ?? []
        })
      }, authStore.token)
    }
    clearCustomPreviewTemplates(userKey)
    remoteTemplates = await apiFetch<PreviewTemplateResource[]>('/api/me/preview-templates', {}, authStore.token)
  }

  customPreviewTemplates.value = remoteTemplates.map(mapPreviewTemplateResource)
}

async function submitModelProfile() {
  if (!isAdminUser.value) {
    modelError.value = '只有管理员可以维护全局模型档案。'
    return
  }
  savingModel.value = true
  modelError.value = ''
  try {
    const trimmedApiKey = modelForm.value.apiKey.trim()
    const payload = {
      ...modelForm.value,
      apiKey: undefined,
      apiKeyEncrypted: trimmedApiKey ? await encryptApiKey(trimmedApiKey, authStore.token) : undefined
    }
    const path = editingModelId.value == null
      ? '/api/me/models'
      : `/api/me/models/${editingModelId.value}`
    const method = editingModelId.value == null ? 'POST' : 'PUT'
    await apiFetch<UserModelProfile>(path, {
      method,
      body: JSON.stringify(payload)
    }, authStore.token)
    beginCreateModel()
    await loadDashboard()
  } catch (error) {
    modelError.value = error instanceof Error ? error.message : '保存模型档案失败。'
  } finally {
    savingModel.value = false
  }
}

async function activateModelProfile(profileId: number) {
  await apiFetch<UserModelProfile>(`/api/me/models/${profileId}/activate`, {
    method: 'POST'
  }, authStore.token)
  await loadDashboard()
}

async function enableModelProfile(profileId: number) {
  await apiFetch<UserModelProfile>(`/api/me/models/${profileId}/enable`, {
    method: 'POST'
  }, authStore.token)
  await loadDashboard()
}

async function disableModelProfile(profileId: number) {
  await apiFetch<void>(`/api/me/models/${profileId}/disable`, {
    method: 'POST'
  }, authStore.token)
  if (previewProfileId.value === profileId) {
    previewProfileId.value = null
    previewResult.value = null
  }
  if (chatModelProfileId.value === profileId) {
    chatModelProfileId.value = null
  }
  await loadDashboard()
}

async function deleteModelProfile(profileId: number) {
  await apiFetch<void>(`/api/me/models/${profileId}`, {
    method: 'DELETE'
  }, authStore.token)
  if (editingModelId.value === profileId) {
    beginCreateModel()
  }
  if (previewProfileId.value === profileId) {
    previewProfileId.value = null
    previewResult.value = null
  }
  if (chatModelProfileId.value === profileId) {
    chatModelProfileId.value = null
  }
  await loadDashboard()
}

function modelAccessStatusLabel(profile: UserModelProfile) {
  switch (profile.accessStatus) {
    case 'APPROVED':
      return profile.enabled ? '已授权' : '已授权未启用'
    case 'PENDING':
      return '审批中'
    case 'REJECTED':
      return '已拒绝'
    default:
      return '未申请'
  }
}

function canPreviewModel(profile: UserModelProfile) {
  return isAdminUser.value || profile.accessStatus === 'APPROVED'
}

async function grantModelAccess() {
  if (!selectedGrantUserId.value || !selectedGrantProfileId.value) {
    modelError.value = '请选择授权用户和模型。'
    return
  }
  grantingModelAccess.value = true
  modelError.value = ''
  try {
    await apiFetch<UserModelProfile>('/api/admin/model-access/grants', {
      method: 'POST',
      body: JSON.stringify({
        userId: selectedGrantUserId.value,
        profileId: selectedGrantProfileId.value,
        makeDefault: grantMakeDefault.value
      })
    }, authStore.token)
    grantMakeDefault.value = false
    await loadAdminModelAccess()
  } catch (error) {
    modelError.value = error instanceof Error ? error.message : '授权模型使用权限失败。'
  } finally {
    grantingModelAccess.value = false
  }
}

async function approveModelAccessRequest(requestId: number) {
  await apiFetch<ModelAccessRequest>(`/api/admin/model-access/requests/${requestId}/approve`, {
    method: 'POST',
    body: JSON.stringify({ comment: '管理员已批准模型使用权限。', makeDefault: false })
  }, authStore.token)
  await loadDashboard()
}

async function rejectModelAccessRequest(requestId: number) {
  await apiFetch<ModelAccessRequest>(`/api/admin/model-access/requests/${requestId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ comment: '管理员暂未批准该模型使用申请。', makeDefault: false })
  }, authStore.token)
  await loadDashboard()
}

function selectPreviewProfile(profile: UserModelProfile) {
  previewProfileId.value = profile.id
}

async function validateModelProfile(profileId: number) {
  validatingModelProfiles.value = { ...validatingModelProfiles.value, [profileId]: true }
  try {
    modelValidationReports.value = {
      ...modelValidationReports.value,
      [profileId]: await apiFetch<ModelValidationReport>(`/api/me/models/${profileId}/validate`, {
        method: 'POST'
      }, authStore.token)
    }
  } finally {
    validatingModelProfiles.value = { ...validatingModelProfiles.value, [profileId]: false }
  }
}

async function runModelPreview() {
  if (previewProfileId.value == null) {
    previewError.value = '请先选择一个模型档案。'
    return
  }
  if (!previewMessage.value.trim()) {
    previewError.value = '请输入试跑消息。'
    return
  }

  previewLoading.value = true
  previewError.value = ''
  previewResult.value = null
  try {
    const imageUrls = [
      ...parseUrlLines(previewImageUrls.value),
      ...previewImageUploads.value.map((item) => item.value)
    ]
    const audioUrls = [
      ...parseUrlLines(previewAudioUrls.value),
      ...previewAudioUploads.value.map((item) => item.value)
    ]
    const extraBody = parseExtraBody(previewExtraBody.value)
    const examples = parsePreviewExamples(previewExamples.value)
    const input: Record<string, unknown> = {}
    if (imageUrls.length > 0) {
      input.imageUrls = imageUrls
    }
    if (audioUrls.length > 0) {
      input.audioUrls = audioUrls
    }
    if (previewThinkingEnabled.value) {
      input.thinking = { type: 'enabled' }
    }
    if (extraBody) {
      input.extraBody = extraBody
    }

    previewResult.value = await apiFetch<ModelPreviewResponse>(`/api/me/models/${previewProfileId.value}/preview`, {
      method: 'POST',
      body: JSON.stringify({
        message: previewMessage.value.trim(),
        systemPrompt: buildPreviewSystemPrompt(),
        examples,
        input: Object.keys(input).length ? input : null
      })
    }, authStore.token)
  } catch (error) {
    previewError.value = error instanceof Error ? error.message : '模型试跑失败。'
  } finally {
    previewLoading.value = false
  }
}

function usePreviewModelForConversation() {
  if (previewResult.value) {
    // 试跑通过后直接把该模型带入新的聊天上下文，减少用户重复切换成本。
    chatModelProfileId.value = previewResult.value.profileId
    activeConversationId.value = ''
    conversationMessages.value = []
  }
}

function clearConversationModelOverride() {
  // 清除临时覆盖后，后续消息会重新回到默认模型选择逻辑。
  chatModelProfileId.value = null
}

async function loadDashboard() {
  loading.value = true
  try {
    const token = authStore.token
    const [capabilitiesPayload, catalogPayload, installationPayload, conversationPayload, mcpPayload] = await Promise.all([
      apiFetch<Capability[]>('/api/capabilities', {}, token),
      apiFetch<CatalogItem[]>('/api/catalog/items', {}, token),
      apiFetch<Installation[]>('/api/me/installations', {}, token),
      apiFetch<ConversationSummary[]>('/api/conversations', {}, token),
      apiFetch<McpServerStatus[]>('/api/me/mcp/servers', {}, token),
      loadModels()
    ])
    capabilities.value = capabilitiesPayload
    catalogItems.value = catalogPayload
    installations.value = installationPayload
    conversations.value = conversationPayload
    mcpStatuses.value = mcpPayload
    await loadCustomTemplates()
    await loadAdminModelAccess()
    await loadAdminOverviewStats()
    ensureInstallForms(catalogPayload, installationPayload)

    if (activeConversationId.value) {
      await loadMessages(activeConversationId.value)
    } else if (conversationPayload.length > 0) {
      await loadMessages(conversationPayload[0].conversationId)
    } else {
      await loadAudit()
    }
  } finally {
    loading.value = false
  }
}

async function loadMessages(conversationId: string) {
  activeConversationId.value = conversationId
  collapsedTimelineRoundKeys.value = []
  const conversation = conversations.value.find((item) => item.conversationId === conversationId)
  const rememberedModelProfileId = conversation?.modelProfileId ?? null
  chatModelProfileId.value = rememberedModelProfileId != null && modelProfiles.value.some((item) => item.id === rememberedModelProfileId)
    ? rememberedModelProfileId
    : null
  conversationMessages.value = await apiFetch<ConversationMessage[]>(`/api/conversations/${encodeURIComponent(conversationId)}/messages`, {}, authStore.token)
  await loadAudit(conversationId)
}

async function loadAudit(conversationId?: string) {
  const params = new URLSearchParams()
  const effectiveConversationId = conversationId ?? activeConversationId.value
  if (effectiveConversationId) {
    params.set('conversationId', effectiveConversationId)
  }
  if (auditStatusFilter.value) {
    params.set('status', auditStatusFilter.value)
  }
  if (auditTypeFilter.value) {
    params.set('capabilityType', auditTypeFilter.value)
  }
  if (auditKeyword.value.trim()) {
    params.set('keyword', auditKeyword.value.trim())
  }
  params.set('limit', '20')
  auditItems.value = await apiFetch<AuditItem[]>(`/api/me/audit?${params.toString()}`, {}, authStore.token)
}

async function applyAuditFilters() {
  await loadAudit()
}

async function clearAuditFilters() {
  auditStatusFilter.value = ''
  auditTypeFilter.value = ''
  auditKeyword.value = ''
  await loadAudit()
}

async function installSkill(itemId: string) {
  if (!isAdminUser.value) {
    installResult.value = {
      message: '只有管理员可以安装全局 Skill，请改用“启用”按钮选择可用能力。',
      nextSteps: [],
      generatedSnippet: undefined
    }
    return
  }
  installResult.value = await apiFetch<InstallResult>('/api/catalog/skills/install', {
    method: 'POST',
    body: JSON.stringify({ itemId })
  }, authStore.token)
  await loadDashboard()
}

async function planServer(itemId: string) {
  if (!isAdminUser.value) {
    installResult.value = {
      message: '只有管理员可以生成或维护全局 MCP 配置模板。',
      nextSteps: [],
      generatedSnippet: undefined
    }
    return
  }
  installResult.value = await apiFetch<InstallResult>(`/api/catalog/mcp-servers/${encodeURIComponent(itemId)}/install-plan`, {
    method: 'POST'
  }, authStore.token)
}

async function installServer(item: CatalogItem) {
  if (!isAdminUser.value) {
    installResult.value = {
      message: '只有管理员可以安装全局 MCP 实例，请改用“启用”按钮选择已开放的能力。',
      nextSteps: [],
      generatedSnippet: undefined
    }
    return
  }
  const config = installForms.value[item.id] ?? {}
  installResult.value = await apiFetch<InstallResult>('/api/catalog/mcp-servers/install', {
    method: 'POST',
    body: JSON.stringify({
      itemId: item.id,
      serverName: config.serverName,
      config
    })
  }, authStore.token)
  await loadDashboard()
}

async function enableCatalogItem(item: CatalogItem) {
  await apiFetch<void>(`/api/catalog/items/${encodeURIComponent(item.type)}/${encodeURIComponent(item.id)}/enable`, {
    method: 'POST'
  }, authStore.token)
  await loadDashboard()
}

async function disableCatalogItem(item: CatalogItem) {
  await apiFetch<void>(`/api/catalog/items/${encodeURIComponent(item.type)}/${encodeURIComponent(item.id)}/disable`, {
    method: 'POST'
  }, authStore.token)
  await loadDashboard()
}

async function updateMcpInstallation(item: Installation) {
  const form = installForms.value[item.itemId] ?? {}
  savingInstallations.value = { ...savingInstallations.value, [item.itemId]: true }
  try {
    installResult.value = await apiFetch<InstallResult>(`/api/me/mcp/installations/${encodeURIComponent(item.itemId)}`, {
      method: 'PUT',
      body: JSON.stringify({
        serverName: form.serverName,
        config: form
      })
    }, authStore.token)
    await loadDashboard()
  } finally {
    savingInstallations.value = { ...savingInstallations.value, [item.itemId]: false }
  }
}

async function validateInstalledServer(serverName: string) {
  validatingServers.value = { ...validatingServers.value, [serverName]: true }
  try {
    validationReports.value = {
      ...validationReports.value,
      [serverName]: await apiFetch<McpValidationReport>(`/api/me/mcp/servers/${encodeURIComponent(serverName)}/validate`, {
        method: 'POST'
      }, authStore.token)
    }
    await loadDashboard()
  } finally {
    validatingServers.value = { ...validatingServers.value, [serverName]: false }
  }
}

async function removeInstallation(itemType: string, itemId: string) {
  await apiFetch<void>(`/api/me/installations/${encodeURIComponent(itemType)}/${encodeURIComponent(itemId)}`, {
    method: 'DELETE'
  }, authStore.token)
  await loadDashboard()
}

async function sendMessage() {
  if (!chatMessage.value.trim()) {
    return
  }
  sending.value = true
  try {
    const response = await apiFetch<AgentResponse>('/api/agent/execute', {
      method: 'POST',
      body: JSON.stringify({
        conversationId: activeConversationId.value || null,
        message: chatMessage.value.trim(),
        modelProfileId: chatModelProfileId.value
      })
    }, authStore.token)
    chatMessage.value = ''
    activeConversationId.value = response.conversationId
    lastUsedModel.value = response.modelSelection ?? null
    await loadDashboard()
    await loadMessages(response.conversationId)
  } finally {
    sending.value = false
  }
}

function startConversation() {
  activeConversationId.value = ''
  conversationMessages.value = []
  collapsedTimelineRoundKeys.value = []
  chatMessage.value = ''
  loadAudit('')
}

async function toggleServerTools(serverName: string) {
  if (expandedServer.value === serverName) {
    expandedServer.value = ''
    return
  }
  if (!toolMap.value[serverName]) {
    toolMap.value[serverName] = await apiFetch<McpToolDefinition[]>(`/api/me/mcp/servers/${encodeURIComponent(serverName)}/tools`, {}, authStore.token)
  }
  expandedServer.value = serverName
}

async function logout() {
  await authStore.logout()
  window.location.href = '/login'
}

onMounted(loadDashboard)
</script>

<template>
  <div class="dashboard-page">
    <header class="dashboard-hero">
      <div>
        <p class="eyebrow">Unified Agent Platform</p>
        <h1>{{ authStore.user?.displayName || authStore.user?.username }} 的智能体工作台</h1>
        <p class="hero-text">
          当前系统已经切换到“管理员维护全局资源，普通用户按需启用”的平台模式。你可以在同一个工作台里查看模型、能力目录、会话轨迹和调用审计。
        </p>
        <div class="model-capability-line">
          <span class="tag-chip">{{ currentRoleLabel }}</span>
          <span class="tag-chip">{{ authStore.user?.role || 'USER' }}</span>
        </div>
        <p class="muted-note">{{ currentRoleDescription }}</p>
      </div>
      <div class="hero-actions">
        <button class="ghost-button" @click="startConversation">新建会话</button>
        <button class="ghost-button" @click="loadDashboard" :disabled="loading">{{ loading ? '刷新中...' : '刷新数据' }}</button>
        <button class="primary-button" @click="logout">退出登录</button>
      </div>
    </header>

    <section class="metric-row">
      <article class="metric-card">
        <span>模型档案</span>
        <strong>{{ modelProfiles.length }}</strong>
      </article>
      <article class="metric-card">
        <span>已安装项</span>
        <strong>{{ installations.length }}</strong>
      </article>
      <article class="metric-card">
        <span>能力总数</span>
        <strong>{{ capabilities.length }}</strong>
      </article>
      <article class="metric-card">
        <span>历史会话</span>
        <strong>{{ conversations.length }}</strong>
      </article>
      <article class="metric-card">
        <span>调用审计</span>
        <strong>{{ auditItems.length }}</strong>
      </article>
    </section>

    <section v-if="isAdminUser && adminOverviewStats" class="panel admin-overview-panel">
      <div class="panel-header stacked-mobile">
        <div>
          <p class="eyebrow">Admin Overview</p>
          <h2>平台资源总览</h2>
        </div>
        <span class="item-state">统计生成时间：{{ adminOverviewStats.generatedAt }}</span>
      </div>

      <div class="metric-row compact-metric-row">
        <article class="metric-card admin-metric-card">
          <span>平台用户</span>
          <strong>{{ adminOverviewStats.summary.totalUsers }}</strong>
          <small>活跃 {{ adminOverviewStats.summary.activeUsers }} / 管理员 {{ adminOverviewStats.summary.adminUsers }}</small>
        </article>
        <article class="metric-card admin-metric-card">
          <span>全局资源</span>
          <strong>{{ adminOverviewStats.summary.managedMcpServers + adminOverviewStats.summary.managedSkills + adminOverviewStats.summary.managedModels }}</strong>
          <small>MCP {{ adminOverviewStats.summary.managedMcpServers }} / Skill {{ adminOverviewStats.summary.managedSkills }} / 模型 {{ adminOverviewStats.summary.managedModels }}</small>
        </article>
        <article class="metric-card admin-metric-card">
          <span>用户启用关系</span>
          <strong>{{ adminOverviewStats.summary.enabledCatalogSelections }}</strong>
          <small>模型已批准 {{ adminOverviewStats.summary.approvedModelSelections }}</small>
        </article>
        <article class="metric-card admin-metric-card">
          <span>待处理审批</span>
          <strong>{{ adminOverviewStats.summary.pendingModelApprovals }}</strong>
          <small>总调用 {{ adminOverviewStats.summary.totalCapabilityInvocations }}</small>
        </article>
      </div>

      <div class="content-grid two-up">
        <article class="simple-item admin-overview-card">
          <div>
            <p class="eyebrow">Trend</p>
            <h3>最近 7 天能力调用趋势</h3>
          </div>
          <div class="simple-list">
            <div v-for="point in adminOverviewStats.invocationTrend" :key="point.date" class="resource-stat-item trend-stat-item">
              <div class="trend-stat-header">
                <strong>{{ point.date }}</strong>
                <span>调用 {{ point.invocationCount }} / 成功 {{ point.successCount }} / 失败 {{ point.failureCount }}</span>
              </div>
              <div class="trend-bar-track">
                <span class="trend-bar-fill" :style="{ width: adminBarWidth(point.invocationCount, adminTrendPeak) }"></span>
              </div>
            </div>
          </div>
        </article>

        <article class="simple-item admin-overview-card">
          <div>
            <p class="eyebrow">Distribution</p>
            <h3>全局资源状态分布</h3>
          </div>
          <div class="simple-list">
            <div v-for="item in adminOverviewStats.resourceDistributions" :key="`${item.itemType}-${item.status}`" class="resource-stat-item distribution-stat-item">
              <div>
                <strong>{{ item.itemType }}</strong>
                <span>{{ adminStatusLabel(item.status) }}</span>
              </div>
              <span class="tag-chip">{{ item.count }}</span>
            </div>
          </div>
        </article>
      </div>

      <article class="simple-item admin-overview-card">
        <div>
          <p class="eyebrow">Approvals</p>
          <h3>模型授权状态分布</h3>
        </div>
        <div class="distribution-chip-row">
          <span
            v-for="item in adminOverviewStats.modelAccessDistributions"
            :key="item.accessStatus"
            class="tag-chip"
          >
            {{ adminStatusLabel(item.accessStatus) }} / {{ item.count }}
          </span>
        </div>
      </article>

      <div class="content-grid two-up">
        <article class="simple-item admin-overview-card">
          <div>
            <p class="eyebrow">Resources</p>
            <h3>全局 MCP / Skill 启用情况</h3>
          </div>
          <div class="simple-list">
            <div v-for="item in adminOverviewStats.managedCatalogItems" :key="`${item.itemType}-${item.itemId}`" class="resource-stat-item">
              <div>
                <strong>{{ item.itemName }}</strong>
                <span>{{ item.itemType }} / {{ item.provider || 'system' }} / {{ item.status }}</span>
                <p class="item-state">启用人数：{{ item.enabledUserCount }}</p>
              </div>
            </div>
          </div>
        </article>

        <article class="simple-item admin-overview-card">
          <div>
            <p class="eyebrow">Models</p>
            <h3>全局模型启用情况</h3>
          </div>
          <div class="simple-list">
            <div v-for="model in adminOverviewStats.models" :key="model.profileId" class="resource-stat-item">
              <div>
                <strong>{{ model.profileName }}</strong>
                <span>{{ model.providerType }} / {{ model.modelId }} / {{ model.enabled ? 'ENABLED' : 'DISABLED' }}</span>
                <p class="item-state">
                  已批准：{{ model.approvedUserCount }} / 待审批：{{ model.pendingUserCount }} / 默认选择：{{ model.defaultUserCount }}
                </p>
              </div>
            </div>
          </div>
        </article>
      </div>

      <article class="simple-item admin-overview-card">
        <div>
          <p class="eyebrow">Hotspots</p>
          <h3>能力调用热度 Top 10</h3>
        </div>
        <div class="simple-list">
          <div v-for="item in adminOverviewStats.topCapabilities" :key="item.capabilityId" class="resource-stat-item">
            <div>
              <strong>{{ item.capabilityName }}</strong>
              <span>{{ item.capabilityType }} / {{ item.provider || 'builtin' }}</span>
              <p class="item-state">
                调用 {{ item.invocationCount }} / 成功 {{ item.successCount }} / 失败 {{ item.failureCount }}
              </p>
              <p class="item-state">{{ item.capabilityId }}</p>
            </div>
          </div>
        </div>
      </article>
    </section>

    <section class="content-grid two-up">
      <article class="panel">
        <div class="panel-header stacked-mobile">
          <div>
            <p class="eyebrow">Models</p>
            <h2>{{ isAdminUser ? '全局模型档案管理' : '模型启用与默认选择' }}</h2>
          </div>
          <button v-if="isAdminUser" class="ghost-button small" @click="beginCreateModel">新建全局模型</button>
        </div>

        <div v-if="activeModel" class="active-model-card">
          <strong>{{ isAdminUser ? '平台对你生效的默认模型' : '你当前的默认模型' }}</strong>
          <span>{{ activeModel.profileName }} / {{ activeModel.modelId }}</span>
          <span>{{ activeModel.baseUrl }}</span>
          <div class="model-capability-line">
            <span class="tag-chip">{{ modelCapabilitySummary(activeModel) }}</span>
            <span class="tag-chip">{{ modelPreferenceSummary(activeModel) }}</span>
            <span v-if="activeModel.hasApiKey" class="tag-chip">已配置 Key：{{ activeModel.maskedApiKey }}</span>
          </div>
        </div>

        <div v-else class="empty-state">
          <strong>还没有可用模型</strong>
          <span>{{ isAdminUser ? '先创建至少一个全局模型档案，用户后续才能启用并参与对话编排。' : '平台当前还没有向你开放可用模型，请联系管理员先配置全局模型档案。' }}</span>
        </div>

        <form v-if="isAdminUser" class="simple-list" @submit.prevent="submitModelProfile">
          <div class="install-form-grid model-form-grid">
            <label class="field-card">
              <span>档案名称 *</span>
              <input v-model="modelForm.profileName" placeholder="例如：OpenAI 主力模型">
            </label>

            <label class="field-card">
              <span>Provider *</span>
              <select v-model="modelForm.providerType">
                <option value="openai-compatible">openai-compatible</option>
                <option value="azure-openai">azure-openai</option>
                <option value="custom">custom</option>
              </select>
            </label>

            <label class="field-card">
              <span>模型地址 *</span>
              <input v-model="modelForm.baseUrl" placeholder="https://api.openai.com/v1">
            </label>

            <label class="field-card">
              <span>模型 ID *</span>
              <input v-model="modelForm.modelId" placeholder="gpt-4.1-mini">
            </label>

            <label class="field-card full-span">
              <span>API Key</span>
              <input v-model="modelForm.apiKey" type="password" placeholder="更新时留空表示保留原 Key">
              <small>后端目前会保存这把 Key，但不会在接口里回传明文。</small>
            </label>
          </div>

          <div class="toggle-grid">
            <label><input v-model="modelForm.supportsText" type="checkbox">文本能力</label>
            <label><input v-model="modelForm.supportsVision" type="checkbox">视觉能力</label>
            <label><input v-model="modelForm.supportsAudio" type="checkbox">音频能力</label>
            <label><input v-model="modelForm.enabled" type="checkbox">启用此模型</label>
            <label><input v-model="modelForm.preferredForGeneralChat" type="checkbox">优先用于通用对话</label>
            <label><input v-model="modelForm.preferredForTools" type="checkbox">优先用于工具调用</label>
            <label><input v-model="modelForm.preferredForSkills" type="checkbox">优先用于 Skills</label>
            <label><input v-model="modelForm.makeDefault" type="checkbox">设为默认模型</label>
          </div>

          <p v-if="modelError" class="error-text">{{ modelError }}</p>

          <div class="model-form-actions">
            <button class="primary-button" :disabled="savingModel">
              {{ savingModel ? '保存中...' : editingModelId == null ? '创建模型档案' : '保存模型档案' }}
            </button>
            <button v-if="editingModelId != null" class="ghost-button" type="button" @click="beginCreateModel">取消编辑</button>
          </div>
        </form>
        <div v-else class="simple-item">
          <strong>管理员维护全局模型，普通用户按需启用</strong>
          <span class="muted-note">
            你现在看到的是平台已经开放的全局模型档案。勾选启用或设为默认后，会只影响你自己的对话编排，不会改动全局配置。
          </span>
        </div>

        <div class="simple-list compact-header">
          <div v-for="profile in modelProfiles" :key="profile.id" class="simple-item model-item">
            <div>
              <strong>{{ profile.profileName }}</strong>
              <span>{{ profile.providerType }} / {{ profile.modelId }}</span>
              <p class="item-state">{{ profile.baseUrl }}</p>
              <div class="model-capability-line">
                <span class="tag-chip">{{ modelCapabilitySummary(profile) }}</span>
                <span class="tag-chip">{{ modelPreferenceSummary(profile) }}</span>
                <span class="tag-chip">{{ modelAccessStatusLabel(profile) }}</span>
                <span v-if="profile.isDefault" class="tag-chip">默认模型</span>
                <span v-if="!profile.enabled" class="tag-chip">已停用</span>
                <span v-if="profile.hasApiKey" class="tag-chip">{{ profile.maskedApiKey }}</span>
              </div>
            </div>
            <div class="hero-actions wrap-start">
              <button v-if="isAdminUser" class="ghost-button small" @click="beginEditModel(profile)">编辑</button>
              <button
                class="ghost-button small"
                :disabled="validatingModelProfiles[profile.id]"
                @click="validateModelProfile(profile.id)"
              >
                {{ validatingModelProfiles[profile.id] ? '验证中...' : '验证模型' }}
              </button>
              <button class="ghost-button small" :disabled="!canPreviewModel(profile)" @click="selectPreviewProfile(profile)">设为试跑目标</button>
              <button v-if="!isAdminUser && profile.accessStatus === 'NOT_REQUESTED'" class="ghost-button small" @click="enableModelProfile(profile.id)">申请使用</button>
              <button v-if="!isAdminUser && profile.accessStatus === 'REJECTED'" class="ghost-button small" @click="enableModelProfile(profile.id)">重新申请</button>
              <button v-if="!isAdminUser && profile.accessStatus === 'PENDING'" class="ghost-button small" disabled>审批中</button>
              <button v-if="profile.accessStatus === 'APPROVED' && !profile.enabled" class="ghost-button small" @click="enableModelProfile(profile.id)">启用</button>
              <button v-if="profile.accessStatus === 'APPROVED' && profile.enabled" class="ghost-button small" :disabled="profile.isDefault" @click="activateModelProfile(profile.id)">设为默认</button>
              <button v-if="profile.accessStatus === 'APPROVED' && profile.enabled && !profile.isDefault" class="ghost-button small" @click="disableModelProfile(profile.id)">停用</button>
              <button v-if="isAdminUser" class="ghost-button small" @click="deleteModelProfile(profile.id)">删除</button>
            </div>
            <div v-if="profile.reviewComment" class="validation-card">
              <strong>审批备注</strong>
              <span>{{ profile.reviewComment }}</span>
            </div>
            <div v-if="modelValidationReports[profile.id]" class="validation-card">
              <strong>模型验证结果</strong>
              <span>{{ modelValidationReports[profile.id].summary }}</span>
              <span v-if="modelValidationReports[profile.id].preview" class="item-state">
                {{ modelValidationReports[profile.id].preview }}
              </span>
            </div>
          </div>
        </div>

        <div v-if="isAdminUser" class="validation-card preview-panel">
          <strong>模型使用审批</strong>
          <span class="muted-note">管理员可以直接给指定用户授权模型，也可以处理普通用户提交的模型使用申请。</span>

          <div class="install-form-grid model-form-grid">
            <label class="field-card">
              <span>授权用户</span>
              <select v-model="selectedGrantUserId">
                <option :value="null">请选择用户</option>
                <option v-for="item in adminUsers" :key="item.userId" :value="item.userId">
                  {{ item.displayName }} / {{ item.username }}
                </option>
              </select>
            </label>
            <label class="field-card">
              <span>授权模型</span>
              <select v-model="selectedGrantProfileId">
                <option :value="null">请选择模型</option>
                <option v-for="profile in modelProfiles" :key="profile.id" :value="profile.id">
                  {{ profile.profileName }} / {{ profile.modelId }}
                </option>
              </select>
            </label>
            <label class="field-card">
              <span>附加设置</span>
              <span><input v-model="grantMakeDefault" type="checkbox"> 授权后设为该用户默认模型</span>
            </label>
          </div>

          <div class="hero-actions">
            <button class="primary-button small" :disabled="grantingModelAccess" @click="grantModelAccess">
              {{ grantingModelAccess ? '授权中...' : '直接授权' }}
            </button>
          </div>

          <div class="simple-list">
            <div v-if="pendingModelAccessRequests.length === 0" class="simple-item">
              <strong>当前没有待审批申请</strong>
              <span class="muted-note">当普通用户提交模型使用申请后，会在这里集中展示。</span>
            </div>
            <div v-for="request in pendingModelAccessRequests" :key="request.requestId" class="simple-item">
              <div>
                <strong>{{ request.displayName }} / {{ request.username }}</strong>
                <span>申请模型：{{ request.profileName }}</span>
                <p class="item-state">申请时间：{{ request.requestedAt || '未知' }}</p>
              </div>
              <div class="hero-actions wrap-start">
                <button class="primary-button small" @click="approveModelAccessRequest(request.requestId)">批准</button>
                <button class="ghost-button small" @click="rejectModelAccessRequest(request.requestId)">拒绝</button>
              </div>
            </div>
          </div>
        </div>
      </article>

      <article class="panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Runtime</p>
            <h2>当前运行时模型</h2>
          </div>
        </div>

        <div v-if="currentExecutionModel" class="active-model-card">
          <strong>本次对话将使用</strong>
          <span>{{ currentExecutionModel.profileName }} / {{ currentExecutionModel.modelId }}</span>
          <span>{{ currentExecutionModel.baseUrl }}</span>
          <div class="model-capability-line">
            <span class="tag-chip" v-if="currentExecutionModel.supportsVision">支持视觉</span>
            <span class="tag-chip" v-if="currentExecutionModel.supportsAudio">支持音频</span>
            <span class="tag-chip" v-if="!currentExecutionModel.supportsVision && !currentExecutionModel.supportsAudio">文本优先</span>
            <span class="tag-chip" v-if="selectedChatModel">当前会话临时覆盖</span>
          </div>
          <div v-if="selectedChatModel" class="hero-actions">
            <button class="ghost-button small" @click="clearConversationModelOverride">恢复默认模型</button>
          </div>
        </div>

        <div v-else class="empty-state">
          <strong>尚未配置可用模型</strong>
          <span>你仍然可以安装 MCP 和 Skills，但真正接入外部模型调用前，建议先完成模型档案配置。</span>
        </div>

        <div class="simple-list">
          <div class="simple-item">
            <strong>使用方式</strong>
            <span class="muted-note">
              当前默认模型会被注入 Agent 编排上下文，后续我们继续接真实 LLM 推理时，会直接读取这里的地址、Key、模型 ID 和多模态能力偏好。
            </span>
          </div>
          <div class="simple-item">
            <strong>与 MCP / Skill 的关系</strong>
            <span class="muted-note">
              现在的资源模型已经调整为“全局配置一次，用户按需启用”。管理员统一维护底座，普通用户只组合自己要用的能力和模型，这样更适合多人协作。
            </span>
          </div>
          <div class="simple-item" v-if="lastUsedModel">
            <strong>最近一次执行使用的模型</strong>
            <span>{{ lastUsedModel.profileName }} / {{ lastUsedModel.modelId }}</span>
            <span class="item-state">{{ lastUsedModel.baseUrl }}</span>
          </div>
        </div>

        <div class="validation-card preview-panel">
          <strong>模型试跑</strong>
          <span class="muted-note">这里可以直接对某个模型做文本或图文试跑，确认配置保存后是否真的可用。</span>

          <label class="field-card">
            <span>试跑模型</span>
            <select v-model="previewProfileId">
              <option :value="null">请选择模型</option>
              <option v-for="profile in modelProfiles" :key="profile.id" :value="profile.id">
                {{ profile.profileName }} / {{ profile.modelId }}
              </option>
            </select>
          </label>

          <label class="field-card">
            <span>试跑消息</span>
            <textarea v-model="previewMessage" rows="4" placeholder="例如：请结合图片内容，描述画面中最显眼的物体。"></textarea>
          </label>

          <PreviewTemplateEditor
            :templates="availablePreviewTemplates"
            :selected-template-id="selectedPreviewTemplateId"
            :active-template-ids="appliedPreviewTemplateIds"
            :custom-template-name="customTemplateName"
            :custom-template-description="customTemplateDescription"
            :can-delete-selected-template="customPreviewTemplates.some((item) => item.id === selectedPreviewTemplateId)"
            :preview-system-prompt="previewSystemPrompt"
            :preview-examples="previewExamples"
            :preview-output-guide="previewOutputGuide"
            :preview-extra-body="previewExtraBody"
            :preview-thinking-enabled="previewThinkingEnabled"
            @update:selected-template-id="selectedPreviewTemplateId = $event"
            @update:custom-template-name="customTemplateName = $event"
            @update:custom-template-description="customTemplateDescription = $event"
            @update:preview-system-prompt="previewSystemPrompt = $event"
            @update:preview-examples="previewExamples = $event"
            @update:preview-output-guide="previewOutputGuide = $event"
            @update:preview-extra-body="previewExtraBody = $event"
            @update:preview-thinking-enabled="previewThinkingEnabled = $event"
            @apply-template="applyPreviewTemplate"
            @append-snippet="appendPreviewSnippet"
            @save-custom-template="saveCurrentAsCustomTemplate"
            @delete-selected-template="deleteSelectedCustomTemplate"
          />

          <label class="field-card">
            <span>图片 URL（可选，多行）</span>
            <textarea
              v-model="previewImageUrls"
              rows="4"
              placeholder="https://example.com/demo-a.png&#10;https://example.com/demo-b.png"
            ></textarea>
            <small>每行一张图片地址，适合做多图试跑。</small>
          </label>

          <label class="field-card">
            <span>本地图片文件（可选）</span>
            <input type="file" accept="image/*" multiple @change="handlePreviewImageFileChange">
            <small>选择后会在浏览器里转成 data URL 直接试跑，不需要先上传到外部图床。</small>
            <span v-if="previewImageUploads.length" class="item-state">
              已选择 {{ previewImageUploads.length }} 张图片：{{ previewImageUploads.map((item) => item.name).join('、') }}
            </span>
            <div v-if="previewImageUploads.length" class="asset-list">
              <div v-for="item in previewImageUploads" :key="item.name + item.sizeBytes" class="asset-item">
                <strong>{{ item.name }}</strong>
                <span>{{ item.mimeType }} / {{ formatFileSize(item.sizeBytes) }}</span>
              </div>
            </div>
          </label>

          <label class="field-card">
            <span>音频 URL（可选，多行）</span>
            <textarea
              v-model="previewAudioUrls"
              rows="3"
              placeholder="https://example.com/demo-audio.mp3"
            ></textarea>
            <small>每行一个音频地址，适合验证支持音频输入的模型或网关。</small>
          </label>

          <label class="field-card">
            <span>本地音频文件（可选）</span>
            <input type="file" accept="audio/*" multiple @change="handlePreviewAudioFileChange">
            <small>选择后会在浏览器里转成 data URL 直接试跑，适合做本地音频输入验证。</small>
            <span v-if="previewAudioUploads.length" class="item-state">
              已选择 {{ previewAudioUploads.length }} 个音频：{{ previewAudioUploads.map((item) => item.name).join('、') }}
            </span>
            <div v-if="previewAudioUploads.length" class="asset-list">
              <div v-for="item in previewAudioUploads" :key="item.name + item.sizeBytes" class="asset-item">
                <strong>{{ item.name }}</strong>
                <span>{{ item.mimeType }} / {{ formatFileSize(item.sizeBytes) }} / {{ formatDuration(item.durationSeconds) }}</span>
              </div>
            </div>
          </label>

          <div v-if="previewUploadWarnings.length" class="warning-box">
            <strong>试跑提示</strong>
            <span v-for="warning in previewUploadWarnings" :key="warning">{{ warning }}</span>
          </div>

          <p v-if="previewError" class="error-text">{{ previewError }}</p>

          <div class="hero-actions">
            <button class="primary-button small" :disabled="previewLoading" @click="runModelPreview">
              {{ previewLoading ? '试跑中...' : '开始试跑' }}
            </button>
          </div>

          <div v-if="previewResult" class="preview-result">
            <strong>{{ previewResult.profileName }} / {{ previewResult.modelId }}</strong>
            <span class="item-state">finishReason: {{ previewResult.finishReason || 'unknown' }}</span>
            <p class="message-content">{{ previewResult.content }}</p>
            <div class="hero-actions">
              <button class="primary-button small" @click="usePreviewModelForConversation">用此模型开始对话</button>
            </div>
            <pre v-if="previewResult.rawResponse" class="message-payload">{{ JSON.stringify(previewResult.rawResponse, null, 2) }}</pre>
          </div>
        </div>
      </article>
    </section>

    <section class="workspace-grid">
      <article class="panel catalog-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Catalog</p>
            <h2>搜索并安装能力</h2>
          </div>
          <input v-model="searchQuery" class="search-input" placeholder="搜索 skills、MCP servers、标签或 transport">
        </div>
        <div class="catalog-list">
          <div v-for="item in filteredCatalog" :key="item.id" class="catalog-item">
            <div class="catalog-item-main">
              <p class="item-type">{{ item.type }} <span v-if="item.transport">/ {{ item.transport }}</span></p>
              <h3>{{ item.name }}</h3>
              <p class="item-description">{{ item.description }}</p>
              <p class="item-state">
                全局可用：{{ item.installed ? '是' : '否' }} · 我的选择：{{ item.userState }} · {{ item.installHint }}
              </p>
              <div class="tag-row">
                <span v-for="tag in item.tags" :key="tag" class="tag-chip">{{ tag }}</span>
              </div>

              <div v-if="item.type === 'MCP_SERVER' && item.installFields.length" class="install-form-grid">
                <label v-for="field in item.installFields" :key="field.key" class="field-card">
                  <span>{{ field.label }}<span v-if="field.required"> *</span></span>
                  <input
                    v-model="installForms[item.id][field.key]"
                    :type="field.secret ? 'password' : 'text'"
                    :placeholder="field.placeholder"
                  >
                  <small>{{ field.description }}</small>
                </label>
              </div>
            </div>
            <div class="item-actions vertical">
              <button
                v-if="item.type === 'AGENT_SKILL' && isAdminUser"
                class="primary-button small"
                @click="installSkill(item.id)"
              >
                全局安装 Skill
              </button>
              <button
                v-if="item.type === 'AGENT_SKILL' && !isAdminUser && item.installed && item.userState !== 'ENABLED'"
                class="primary-button small"
                @click="enableCatalogItem(item)"
              >
                启用 Skill
              </button>
              <button
                v-if="item.type === 'AGENT_SKILL' && !isAdminUser && item.userState === 'ENABLED'"
                class="ghost-button small"
                @click="disableCatalogItem(item)"
              >
                取消启用
              </button>
              <template v-if="item.type === 'MCP_SERVER'">
                <button v-if="isAdminUser" class="primary-button small" @click="installServer(item)">全局安装 MCP</button>
                <button v-if="isAdminUser" class="ghost-button small" @click="planServer(item.id)">生成配置模板</button>
                <button
                  v-if="!isAdminUser && item.installed && item.userState !== 'ENABLED'"
                  class="primary-button small"
                  @click="enableCatalogItem(item)"
                >
                  启用 MCP
                </button>
                <button
                  v-if="!isAdminUser && item.userState === 'ENABLED'"
                  class="ghost-button small"
                  @click="disableCatalogItem(item)"
                >
                  取消启用
                </button>
              </template>
            </div>
          </div>
        </div>
      </article>

      <article class="panel conversation-panel">
        <div class="panel-header stacked-mobile">
          <div>
            <p class="eyebrow">Conversation</p>
            <h2>{{ activeConversationTitle }}</h2>
          </div>
          <div class="hero-actions">
            <button class="ghost-button small" @click="startConversation">新会话</button>
          </div>
        </div>

        <div v-if="currentExecutionModel" class="active-model-card">
          <strong>当前对话默认模型</strong>
          <span>{{ currentExecutionModel.profileName }} / {{ currentExecutionModel.modelId }}</span>
          <span>{{ currentExecutionModel.baseUrl }}</span>
          <span v-if="selectedChatModel" class="item-state">当前消息将优先使用你刚才指定的临时模型，不会修改全局默认设置。</span>
        </div>

        <div class="conversation-layout">
          <aside class="conversation-sidebar">
            <button
              v-for="conversation in conversations"
              :key="conversation.conversationId"
              class="conversation-item"
              :class="{ active: conversation.conversationId === activeConversationId }"
              @click="loadMessages(conversation.conversationId)"
            >
              <strong>{{ conversation.title }}</strong>
              <span>{{ conversation.updatedAt }}</span>
            </button>
          </aside>

          <div class="conversation-main">
            <div class="message-list">
              <div v-if="!conversationMessages.length" class="empty-state">
                <strong>还没有消息</strong>
                <span>安装好 Skill 或 MCP 后，就可以直接在这里开始对话。</span>
              </div>
              <div
                v-for="message in conversationMessages"
                :key="message.id"
                class="message-card"
                :class="message.role === 'USER' ? 'message-user' : 'message-assistant'"
              >
                <p class="message-role">{{ message.role }}</p>
                <p class="message-content">{{ message.content }}</p>
                <pre v-if="message.payloadJson" class="message-payload">{{ message.payloadJson }}</pre>
              </div>
            </div>

            <div class="composer">
              <textarea v-model="chatMessage" rows="5" placeholder="输入你的任务，例如：帮我使用已安装的 filesystem MCP 查看当前工作区结构"></textarea>
              <div class="hero-actions">
                <button class="primary-button" :disabled="sending" @click="sendMessage">{{ sending ? '发送中...' : '发送消息' }}</button>
              </div>
            </div>
          </div>
        </div>
      </article>
    </section>

    <section class="content-grid two-up">
      <article class="panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Enabled</p>
            <h2>我启用的能力</h2>
          </div>
        </div>
        <div class="simple-list">
          <div v-for="item in installations" :key="`${item.itemType}-${item.itemId}`" class="simple-item installation-item">
            <div>
              <strong>{{ item.itemName }}</strong>
              <span>{{ item.itemType }} / {{ item.status }} / {{ item.provider }}</span>
              <p v-if="installationMetadataSummary(item)" class="item-state">{{ installationMetadataSummary(item) }}</p>
            </div>

            <div v-if="item.itemType === 'MCP_SERVER' && catalogById(item.itemId)" class="install-form-grid installation-form-grid">
              <label
                v-for="field in catalogById(item.itemId)?.installFields ?? []"
                :key="`${item.itemId}-${field.key}`"
                class="field-card"
              >
                <span>{{ field.label }}<span v-if="field.required"> *</span></span>
                <input
                  v-model="installForms[item.itemId][field.key]"
                  :type="field.secret ? 'password' : 'text'"
                  :placeholder="field.placeholder"
                >
                <small>{{ field.description }}</small>
              </label>
            </div>

            <div class="hero-actions wrap-start">
              <button
                v-if="item.itemType === 'MCP_SERVER' && isAdminUser"
                class="primary-button small"
                :disabled="savingInstallations[item.itemId]"
                @click="updateMcpInstallation(item)"
              >
                {{ savingInstallations[item.itemId] ? '保存中...' : '保存 MCP 配置' }}
              </button>
              <button
                v-if="item.itemType === 'MCP_SERVER'"
                class="ghost-button small"
                :disabled="validatingServers[installedServerName(item)]"
                @click="validateInstalledServer(installedServerName(item))"
              >
                {{ validatingServers[installedServerName(item)] ? '验证中...' : '验证实例' }}
              </button>
              <button class="ghost-button small" @click="removeInstallation(item.itemType, item.itemId)">移除</button>
            </div>

            <div v-if="item.itemType === 'MCP_SERVER' && validationReports[installedServerName(item)]" class="validation-card">
              <strong>验证结果</strong>
              <span>{{ validationReports[installedServerName(item)].summary }}</span>
              <span>{{ validationReports[installedServerName(item)].configurationSummary }}</span>
              <div class="validation-stats">
                <span>Tools：{{ validationReports[installedServerName(item)].toolCount }}</span>
                <span>Resources：{{ validationReports[installedServerName(item)].resourceCount }}</span>
                <span>Prompts：{{ validationReports[installedServerName(item)].promptCount }}</span>
              </div>
              <div class="check-list">
                <div v-for="(value, key) in validationReports[installedServerName(item)].checks" :key="key" class="check-item">
                  <strong>{{ key }}</strong>
                  <span>{{ value }}</span>
                </div>
              </div>
              <div v-if="validationReports[installedServerName(item)].diagnostics.length" class="diagnostic-list">
                <div v-for="diagnostic in validationReports[installedServerName(item)].diagnostics" :key="diagnostic.code" class="diagnostic-item">
                  <strong>{{ diagnostic.severity }} / {{ diagnostic.code }}</strong>
                  <span>{{ diagnostic.message }}</span>
                  <span v-if="diagnostic.suggestion">{{ diagnostic.suggestion }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </article>

      <article class="panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">MCP</p>
            <h2>我的 MCP 状态</h2>
          </div>
        </div>
        <div class="simple-list">
          <div v-for="server in mcpStatuses" :key="server.serverName" class="simple-item server-item">
            <div>
              <strong>{{ server.serverName }}</strong>
              <span>{{ server.connectionState }} / {{ server.protocolVersion || '未协商' }}</span>
              <p v-if="server.message" class="item-state">{{ server.message }}</p>
            </div>
            <button class="ghost-button small" @click="toggleServerTools(server.serverName)">
              {{ expandedServer === server.serverName ? '收起 Tools' : '查看 Tools' }}
            </button>
            <div v-if="expandedServer === server.serverName" class="tool-list">
              <div v-for="tool in toolMap[server.serverName] ?? []" :key="tool.name" class="tool-item">
                <strong>{{ tool.title || tool.name }}</strong>
                <span>{{ tool.description || '无描述' }}</span>
              </div>
            </div>
          </div>
        </div>
      </article>
    </section>

    <section class="panel">
      <div class="panel-header audit-header stacked-mobile">
        <div>
          <p class="eyebrow">Audit</p>
          <h2>{{ auditHeading }}</h2>
        </div>
        <div class="audit-filter-grid">
          <select v-model="auditStatusFilter">
            <option value="">全部状态</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILED">FAILED</option>
          </select>
          <select v-model="auditTypeFilter">
            <option value="">全部类型</option>
            <option value="BUILTIN">BUILTIN</option>
            <option value="SKILL">SKILL</option>
            <option value="MCP_TOOL">MCP_TOOL</option>
          </select>
          <input v-model="auditKeyword" placeholder="按能力名、ID、错误关键字筛选">
          <button class="ghost-button small" @click="applyAuditFilters">应用筛选</button>
          <button class="ghost-button small" @click="clearAuditFilters">清空</button>
        </div>
      </div>
      <div v-if="activeConversationId && conversationTimelineRounds.length" class="timeline-round-list">
        <div v-for="round in conversationTimelineRounds" :key="round.key" class="timeline-round">
          <button class="timeline-round-header" type="button" @click="toggleTimelineRound(round.key)">
            <div>
              <strong>{{ round.userMessage ? `本轮提问：${round.title}` : round.title }}</strong>
              <span>{{ round.startedAt }}</span>
              <p class="item-state">{{ timelineRoundSummary(round) }}</p>
              <div v-if="timelineRoundChips(round).length" class="model-capability-line">
                <span v-for="chip in timelineRoundChips(round)" :key="`${round.key}-${chip}`" class="tag-chip">
                  {{ chip }}
                </span>
              </div>
            </div>
            <span class="tag-chip">{{ isTimelineRoundCollapsed(round.key) ? '展开' : '收起' }}</span>
          </button>
          <div v-if="!isTimelineRoundCollapsed(round.key)" class="timeline-list">
            <div v-for="entry in round.items" :key="entry.key" class="timeline-item">
              <div class="timeline-rail">
                <span class="timeline-dot"></span>
              </div>
              <div class="timeline-body">
                <template v-if="entry.kind === 'message' && entry.message">
                  <div class="panel-header stacked-mobile">
                    <div>
                      <strong>{{ conversationMessageRoleLabel(entry.message.role) }}</strong>
                      <span>{{ entry.createdAt }}</span>
                    </div>
                    <span class="tag-chip" :class="conversationMessageRoleClass(entry.message.role)">
                      {{ conversationMessageRoleLabel(entry.message.role) }}
                    </span>
                  </div>
                  <p class="muted-note">{{ timelineMessagePreview(entry.message) }}</p>
                  <pre v-if="entry.message.payloadJson" class="message-payload">{{ entry.message.payloadJson }}</pre>
                </template>
                <template v-else-if="entry.audit">
                  <div class="panel-header stacked-mobile">
                    <div>
                      <strong>{{ timelineAuditTitle(entry.audit) }}</strong>
                      <span>{{ entry.createdAt }}</span>
                    </div>
                    <div class="hero-actions wrap-start">
                      <span class="tag-chip" :class="auditStatusClass(entry.audit.status)">{{ auditStatusLabel(entry.audit.status) }}</span>
                      <span v-if="auditDurationLabel(entry.audit.durationMs)" class="tag-chip">{{ auditDurationLabel(entry.audit.durationMs) }}</span>
                    </div>
                  </div>
                  <p class="item-state">{{ entry.audit.capabilityId }}</p>
                  <p class="muted-note">{{ auditPreview(entry.audit) }}</p>
                  <div v-if="auditSummaryChips(entry.audit).length" class="model-capability-line">
                    <span v-for="chip in auditSummaryChips(entry.audit)" :key="`${entry.key}-${chip}`" class="tag-chip">
                      {{ chip }}
                    </span>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="simple-list">
        <div v-if="!auditItems.length" class="empty-state">
          <strong>当前没有审计记录</strong>
          <span>发送一次对话或调用一次能力后，这里会出现执行轨迹。</span>
        </div>
        <div v-for="item in auditDisplayItems" :key="item.id" class="simple-item audit-item">
          <div class="panel-header stacked-mobile">
            <div>
              <strong>{{ item.capabilityName }}</strong>
              <span>{{ item.capabilityType }} / {{ item.provider || 'builtin' }} / {{ item.createdAt }}</span>
              <p class="item-state">{{ item.capabilityId }}</p>
            </div>
            <div class="hero-actions wrap-start">
              <span class="tag-chip" :class="auditStatusClass(item.status)">{{ auditStatusLabel(item.status) }}</span>
              <button class="ghost-button small" @click="toggleAuditItem(item.id)">
                {{ expandedAuditId === item.id ? '收起详情' : '查看详情' }}
              </button>
            </div>
          </div>
          <span v-if="item.conversationId" class="item-state">会话：{{ item.conversationId }}</span>
          <span v-if="activeConversationId" class="item-state audit-step-label">{{ auditTimelineStep(item.id) }}</span>
          <span class="muted-note">{{ auditPreview(item) }}</span>
          <div v-if="auditSummaryChips(item).length" class="model-capability-line">
            <span v-for="chip in auditSummaryChips(item)" :key="`${item.id}-${chip}`" class="tag-chip">
              {{ chip }}
            </span>
          </div>
          <pre v-if="item.errorMessage" class="message-payload">{{ item.errorMessage }}</pre>
          <div v-if="expandedAuditId === item.id" class="audit-detail-card">
            <div v-if="auditPhaseDurations(item).length" class="asset-item">
              <strong>阶段耗时</strong>
              <div class="model-capability-line">
                <span v-for="phase in auditPhaseDurations(item)" :key="`${item.id}-${phase}`" class="tag-chip">
                  {{ phase }}
                </span>
              </div>
            </div>
            <div v-if="item.traceJson" class="asset-item">
              <strong>模型轨迹</strong>
              <span v-if="auditTraceModelLabel(item)" class="item-state">{{ auditTraceModelLabel(item) }}</span>
              <pre class="message-payload">{{ prettyAuditJson(item.traceJson) }}</pre>
            </div>
            <div class="asset-item">
              <strong>请求载荷</strong>
              <pre class="message-payload">{{ prettyAuditJson(item.requestJson) || '无请求载荷' }}</pre>
            </div>
            <div class="asset-item">
              <strong>执行结果</strong>
              <pre class="message-payload">{{ prettyAuditJson(item.resultJson) || '无结果载荷' }}</pre>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="panel" v-if="installResult">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Result</p>
          <h2>最新安装结果</h2>
        </div>
      </div>
      <p>{{ installResult.message }}</p>
      <ul class="step-list">
        <li v-for="step in installResult.nextSteps" :key="step">{{ step }}</li>
      </ul>
      <pre v-if="installResult.generatedSnippet" class="snippet">{{ installResult.generatedSnippet }}</pre>
    </section>
  </div>
</template>
