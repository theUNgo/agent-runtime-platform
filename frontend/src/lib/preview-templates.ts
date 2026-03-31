/**
 * 模型试跑模板定义。
 * 这里统一沉淀场景化预设，避免模板元数据散落在页面脚本中。
 */
export interface PreviewExtraBodyTemplate {
  id: string
  label: string
  category: string
  description: string
  payload: Record<string, unknown>
  recommendedSystemPrompt?: string
  recommendedMessage?: string
  recommendedExamples?: Array<Record<string, unknown>>
  recommendedOutputGuide?: string
  recommendedThinkingEnabled?: boolean
  recommendedNotes?: string[]
  snippets?: PreviewTemplateSnippet[]
}

/**
 * 模板片段定义。
 * 用于把常用 few-shot、字段说明或系统提示词块按需追加到当前编辑内容中，
 * 避免一选模板就覆盖掉用户已经微调好的内容。
 */
export interface PreviewTemplateSnippet {
  id: string
  label: string
  target: 'systemPrompt' | 'examples' | 'outputGuide'
  description: string
  content: string
}

/**
 * 当前工作台内置的试跑模板。
 * 这里先覆盖结构化输出、多模态理解和智谱兼容场景，后续可以继续扩展。
 */
export const previewExtraBodyTemplates: PreviewExtraBodyTemplate[] = [
  {
    id: 'glm-thinking',
    label: '智谱 Thinking',
    category: '供应商参数',
    description: '适合 GLM 兼容接口的基础思维增强参数，会自动建议开启 thinking。',
    payload: {},
    recommendedSystemPrompt: '你是一个擅长逐步分析和解释判断依据的智能助手。',
    recommendedThinkingEnabled: true,
    recommendedNotes: ['适合需要更强分析过程的问答或视觉理解任务。']
  },
  {
    id: 'glm-grounding',
    label: '智谱视觉定位',
    category: '多模态场景',
    description: '适合做目标定位、框选和坐标输出类联调。',
    payload: {
      metadata: {
        scene: 'vision-grounding',
        source: 'agent-runtime-ui'
      }
    },
    recommendedSystemPrompt: '你是一个视觉定位助手。请严格根据图片内容定位目标，并优先返回坐标结果。',
    recommendedMessage: '请定位图片中目标对象，并仅输出 [[xmin,ymin,xmax,ymax]] 格式的坐标。',
    recommendedExamples: [
      {
        role: 'user',
        content: '请定位桌面右侧的红色杯子，并仅输出 [[xmin,ymin,xmax,ymax]]。'
      },
      {
        role: 'assistant',
        content: '[[412,188,503,344]]'
      }
    ],
    recommendedOutputGuide: '输出要求：\n1. 只返回一个坐标框。\n2. 格式必须是 [[xmin,ymin,xmax,ymax]]。\n3. 不要附加解释、单位或自然语言。',
    recommendedThinkingEnabled: true,
    recommendedNotes: ['建议至少上传 1 张图片。', '如果要定位多个目标，最好在提示词中明确顺序和数量。'],
    snippets: [
      {
        id: 'grounding-strict-output',
        label: '追加严格坐标规则',
        target: 'outputGuide',
        description: '补一段更严格的坐标输出约束。',
        content: '补充要求：如果无法确认目标，请返回 [[-1,-1,-1,-1]]，不要猜测。'
      }
    ]
  },
  {
    id: 'glm-grounding-multi',
    label: '多目标定位',
    category: '多模态场景',
    description: '适合一次返回多个目标框或按顺序返回目标列表。',
    payload: {
      metadata: {
        scene: 'vision-grounding-multi',
        source: 'agent-runtime-ui'
      }
    },
    recommendedSystemPrompt: '你是一个多目标视觉定位助手。请按用户要求顺序输出多个目标坐标。',
    recommendedMessage: '请依次定位图中的 3 个目标，并返回 JSON 数组，每项包含 label 和 bbox。',
    recommendedExamples: [
      {
        role: 'user',
        content: '请依次定位左侧瓶子和右侧杯子，返回 [{"label":"","bbox":[xmin,ymin,xmax,ymax]}]。'
      },
      {
        role: 'assistant',
        content: '[{"label":"瓶子","bbox":[44,122,109,330]},{"label":"杯子","bbox":[266,148,321,286]}]'
      }
    ],
    recommendedOutputGuide: '返回要求：\n- 输出 JSON 数组\n- 每项包含 label 和 bbox\n- bbox 固定为 [xmin,ymin,xmax,ymax]',
    recommendedThinkingEnabled: true,
    recommendedNotes: ['适合多目标识别、清点和顺序定位场景。'],
    snippets: [
      {
        id: 'grounding-multi-example',
        label: '追加多目标示例',
        target: 'examples',
        description: '再补一组多目标返回示例，帮助模型稳定输出 JSON 数组。',
        content: '[{"role":"user","content":"请定位左侧书本和中间手机，返回数组。"},{"role":"assistant","content":"[{\\"label\\":\\"书本\\",\\"bbox\\":[24,118,128,302]},{\\"label\\":\\"手机\\",\\"bbox\\":[210,144,274,301]}]"}]'
      }
    ]
  },
  {
    id: 'glm-ocr-json',
    label: '图片 OCR 抽取',
    category: '多模态场景',
    description: '适合票据、表单、截图文字抽取，并优先输出 JSON。',
    payload: {
      response_format: { type: 'json_object' },
      metadata: {
        scene: 'vision-ocr',
        source: 'agent-runtime-ui'
      }
    },
    recommendedSystemPrompt: '你是一个 OCR 与字段抽取助手。请保持字段名稳定，优先返回结构化结果。',
    recommendedMessage: '请识别图片中的文字，并按 JSON 返回标题、正文和关键字段。',
    recommendedExamples: [
      {
        role: 'user',
        content: '请把海报中的关键信息提取成 JSON，字段包含 title、date、location。'
      },
      {
        role: 'assistant',
        content: '{"title":"开发者大会","date":"2026-04-18","location":"上海"}'
      }
    ],
    recommendedOutputGuide: '建议字段：\n- title: 标题\n- date: 日期\n- location: 地点\n- body: 正文摘要\n如果字段不存在，请返回空字符串或 null。',
    recommendedNotes: ['适合截图、票据、海报或表格截图。', '如果字段固定，建议再补充具体 JSON 字段要求。'],
    snippets: [
      {
        id: 'ocr-field-block',
        label: '追加 OCR 字段块',
        target: 'outputGuide',
        description: '补充更完整的 OCR 结构化字段说明。',
        content: '扩展字段：\n- phone: 联系电话\n- address: 地址\n- amount: 金额\n- confidence: 识别置信度'
      },
      {
        id: 'ocr-example-receipt',
        label: '追加票据示例',
        target: 'examples',
        description: '补一组票据抽取 few-shot 示例。',
        content: '[{"role":"user","content":"请从这张小票中提取 merchant、date、total。"},{"role":"assistant","content":"{\\"merchant\\":\\"便利店\\",\\"date\\":\\"2026-03-31\\",\\"total\\":\\"45.80\\"}"}]'
      }
    ]
  },
  {
    id: 'structured-json',
    label: '结构化 JSON',
    category: '结构化输出',
    description: '要求模型优先输出 JSON 结果，适合抽取类联调。',
    payload: {
      response_format: { type: 'json_object' }
    },
    recommendedSystemPrompt: '你是一个结构化抽取助手。除 JSON 外不要输出其他说明文字。',
    recommendedMessage: '请将结果整理成 JSON 输出，不要附加额外解释。',
    recommendedExamples: [
      {
        role: 'user',
        content: '请返回一个 JSON，包含 sentiment 和 summary。'
      },
      {
        role: 'assistant',
        content: '{"sentiment":"positive","summary":"整体反馈积极"}'
      }
    ],
    recommendedOutputGuide: '建议字段：\n- sentiment: 情绪标签\n- summary: 简要总结\n- confidence: 置信度（可选）\n注意：除 JSON 外不要输出其他说明。',
    recommendedNotes: ['适合信息抽取、分类、标签生成和规则判定。', '通常建议把 temperature 控制在较低范围。'],
    snippets: [
      {
        id: 'json-field-block',
        label: '追加通用字段块',
        target: 'outputGuide',
        description: '给结构化输出补一段常见字段建议。',
        content: '可选字段：\n- category: 分类标签\n- keywords: 关键词数组\n- risks: 风险点数组'
      },
      {
        id: 'json-example-binary',
        label: '追加判定示例',
        target: 'examples',
        description: '适合二分类或规则判断场景。',
        content: '[{"role":"user","content":"请判断文本是否包含风险词，并返回 JSON。"},{"role":"assistant","content":"{\\"matched\\":true,\\"keywords\\":[\\"违规\\"],\\"summary\\":\\"命中高风险词\\"}"}]'
      }
    ]
  },
  {
    id: 'structured-json-schema',
    label: '结构化 Schema',
    category: '结构化输出',
    description: '使用 json_schema 约束输出格式，适合严格字段验证场景。',
    payload: {
      response_format: {
        type: 'json_schema',
        json_schema: {
          name: 'preview_result',
          schema: {
            type: 'object',
            properties: {
              summary: { type: 'string' },
              labels: {
                type: 'array',
                items: { type: 'string' }
              },
              confidence: { type: 'number' }
            },
            required: ['summary'],
            additionalProperties: false
          }
        }
      }
    },
    recommendedSystemPrompt: '你是一个严格遵守 JSON Schema 的输出助手，字段和类型都必须符合约束。',
    recommendedMessage: '请按给定 schema 返回结果，确保字段完整且类型正确。',
    recommendedOutputGuide: '如果模型未能严格遵守 schema，可先改用“结构化 JSON”模板做兼容性回退，再逐步收紧约束。',
    recommendedNotes: ['部分 OpenAI-compatible 网关未必完整支持 json_schema，可先用 JSON 模板做回退验证。'],
    snippets: [
      {
        id: 'schema-strict-prompt',
        label: '追加严格 schema 约束',
        target: 'systemPrompt',
        description: '进一步强调必须严格遵守 schema。',
        content: '如果无法满足 schema，请返回最接近 schema 的安全默认值，不要输出自然语言解释。'
      }
    ]
  },
  {
    id: 'image-summary',
    label: '图片摘要',
    category: '多模态场景',
    description: '适合图片理解、场景总结和视觉问答前的基础冒烟验证。',
    payload: {
      metadata: {
        scene: 'image-summary',
        source: 'agent-runtime-ui'
      }
    },
    recommendedSystemPrompt: '你是一个视觉理解助手，请优先总结主体、动作、场景和重要文字。',
    recommendedMessage: '请总结图片中的主体、场景、关键动作和可能的重要文字信息。',
    recommendedNotes: ['适合快速确认视觉模型是否能正常读取图片。']
  },
  {
    id: 'vision-compare',
    label: '多图对比',
    category: '多模态场景',
    description: '适合两张或多张图片的差异对比、版本变更和异常查找。',
    payload: {
      metadata: {
        scene: 'vision-compare',
        source: 'agent-runtime-ui'
      }
    },
    recommendedSystemPrompt: '你是一个多图对比助手，需要明确指出图片之间的差异、缺失项和新增项。',
    recommendedMessage: '请比较这些图片的差异，并按“新增、缺失、变化”三部分输出。',
    recommendedOutputGuide: '建议输出结构：\n- 新增项\n- 缺失项\n- 变化项\n如果没有差异，请明确说明“未发现明显差异”。',
    recommendedNotes: ['至少上传 2 张图片。', '适合前后版本截图、UI 回归和监控画面对比。'],
    snippets: [
      {
        id: 'vision-compare-example',
        label: '追加差异示例',
        target: 'examples',
        description: '补一组差异对比 few-shot 示例。',
        content: '[{"role":"user","content":"请比较这两张界面截图的差异。"},{"role":"assistant","content":"{\\"新增项\\":[\\"右上角新增按钮\\"],\\"缺失项\\":[\\"底部帮助文案\\"],\\"变化项\\":[\\"主按钮颜色从蓝色变为绿色\\"]}"}]'
      }
    ]
  },
  {
    id: 'audio-summary',
    label: '音频摘要',
    category: '多模态场景',
    description: '适合语音摘要、音频内容概览和转写前的基础验证。',
    payload: {
      metadata: {
        scene: 'audio-summary',
        source: 'agent-runtime-ui'
      }
    },
    recommendedSystemPrompt: '你是一个音频理解助手，请先概括主要内容，再提炼关键信息。',
    recommendedMessage: '请先概括音频主要内容，再列出 3 条关键信息。',
    recommendedNotes: ['建议上传较短音频做首轮联调，方便快速排查格式或时长限制。']
  },
  {
    id: 'audio-transcript-qa',
    label: '音频转写问答',
    category: '多模态场景',
    description: '适合先转写再回答问题的音频理解场景。',
    payload: {
      metadata: {
        scene: 'audio-transcript-qa',
        source: 'agent-runtime-ui'
      }
    },
    recommendedSystemPrompt: '你是一个音频转写与问答助手。请先理解音频内容，再针对用户问题回答。',
    recommendedMessage: '请先转写音频中的关键信息，再回答：说话人最主要的结论是什么？',
    recommendedOutputGuide: '建议输出结构：\n- transcript_summary\n- answer\n- evidence',
    recommendedNotes: ['适合会议片段、电话录音和语音备忘。'],
    snippets: [
      {
        id: 'audio-qa-example',
        label: '追加音频问答示例',
        target: 'examples',
        description: '补充“先转写后问答”的 few-shot 示例。',
        content: '[{"role":"user","content":"请先转写再回答：说话人是否同意延期？"},{"role":"assistant","content":"{\\"transcript_summary\\":\\"说话人表示项目可以延期一周\\",\\"answer\\":\\"是，同意延期一周\\",\\"evidence\\":\\"他说‘可以往后顺一周’\\"}"}]'
      }
    ]
  },
  {
    id: 'trace-metadata',
    label: '联调元数据',
    category: '供应商参数',
    description: '给网关附带简单元信息，方便服务端排查请求来源。',
    payload: {
      metadata: {
        scene: 'preview',
        source: 'agent-runtime-ui'
      }
    },
    recommendedNotes: ['适合联调阶段排查请求来源、环境和页面入口。']
  },
  {
    id: 'low-temperature-json',
    label: '低温抽取模式',
    category: '供应商参数',
    description: '适合希望结果更稳定、更接近规则抽取的场景。',
    payload: {
      temperature: 0.1,
      top_p: 0.2
    },
    recommendedSystemPrompt: '你是一个稳定、保守的抽取助手，优先保证字段正确和结果一致性。',
    recommendedNotes: ['适合结构化抽取、字段比对和规则判断。', '可以和结构化 JSON 模板叠加思路一起使用。'],
    snippets: [
      {
        id: 'low-temp-rule-note',
        label: '追加稳定性说明',
        target: 'systemPrompt',
        description: '强调优先稳定和保守回答。',
        content: '优先复用输入中的明确信息；如果证据不足，请返回 null 或“无法确认”，不要脑补。'
      }
    ]
  }
]
