import type { PreviewExtraBodyTemplate } from './preview-templates'

export interface CustomPreviewTemplate extends PreviewExtraBodyTemplate {
  source: 'custom'
  createdAt: string
  updatedAt: string
}

const STORAGE_PREFIX = 'agent-runtime-preview-templates'

/**
 * 读取指定用户的自定义试跑模板。
 * 这里先使用浏览器本地存储做轻量持久化，避免在后端接口落地前阻塞用户联调。
 */
export function loadCustomPreviewTemplates(userKey: string): CustomPreviewTemplate[] {
  if (!userKey || typeof localStorage === 'undefined') {
    return []
  }
  try {
    const raw = localStorage.getItem(storageKey(userKey))
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed.filter(isCustomTemplate)
  } catch {
    return []
  }
}

/**
 * 保存或更新自定义模板。
 */
export function saveCustomPreviewTemplate(userKey: string, template: CustomPreviewTemplate) {
  const templates = loadCustomPreviewTemplates(userKey)
  const next = templates.filter((item) => item.id !== template.id)
  next.push(template)
  persistTemplates(userKey, next)
}

/**
 * 删除指定的自定义模板。
 */
export function removeCustomPreviewTemplate(userKey: string, templateId: string) {
  const templates = loadCustomPreviewTemplates(userKey)
  persistTemplates(userKey, templates.filter((item) => item.id !== templateId))
}

/**
 * 清空指定用户的本地模板缓存。
 * 当前主要用于把历史本地模板迁移到服务端后做一次性清理。
 */
export function clearCustomPreviewTemplates(userKey: string) {
  if (!userKey || typeof localStorage === 'undefined') {
    return
  }
  localStorage.removeItem(storageKey(userKey))
}

function persistTemplates(userKey: string, templates: CustomPreviewTemplate[]) {
  if (!userKey || typeof localStorage === 'undefined') {
    return
  }
  localStorage.setItem(storageKey(userKey), JSON.stringify(templates))
}

function storageKey(userKey: string) {
  return `${STORAGE_PREFIX}:${userKey}`
}

function isCustomTemplate(value: unknown): value is CustomPreviewTemplate {
  if (typeof value !== 'object' || value == null || Array.isArray(value)) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return typeof candidate.id === 'string'
    && typeof candidate.label === 'string'
    && typeof candidate.category === 'string'
    && typeof candidate.description === 'string'
    && typeof candidate.source === 'string'
    && candidate.source === 'custom'
}
