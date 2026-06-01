export interface Message {
  id: string
  role: 'user' | 'assistant' | 'tool' | 'system'
  content: string | null
  toolCallId?: string
  toolName?: string
  toolCalls?: Array<{ id: string; name: string; arguments: string }>
  createdAt: string
}

export interface ToolExecution {
  toolCallId: string
  toolName: string
  status: 'SUCCESS' | 'ERROR' | 'DENIED'
  content: string
}

export interface ChatResponse {
  runId: string
  sessionId: string
  traceId?: string
  finalMessage?: {
    role: 'assistant'
    content: string | null
  }
  content: string | null
  status: string
  toolExecutions: ToolExecution[]
  usage?: { inputTokens: number; outputTokens: number }
  error?: string
}

export interface ToolCall {
  id: string
  name: string
  arguments: string
}

export type StreamEvent =
  | { type: 'start'; runId: string; sessionId: string }
  | { type: 'text_delta'; text: string }
  | { type: 'tool_call'; toolCall: ToolCall }
  | { type: 'tool_result'; toolResult: ToolExecution }
  | { type: 'trace'; trace: TraceEvent }
  | { type: 'done'; response: ChatResponse }
  | { type: 'error'; message: string }

export interface TraceEvent {
  id: string
  runId: string
  sessionId: string
  stepIndex: number
  eventType: string
  payload: Record<string, unknown>
  createdAt: string
}

export interface SkillIndex {
  skillId: string
  name: string
  description: string
  status: 'ACTIVE' | 'ARCHIVED'
  tags?: string[]
  pinned: boolean
  useCount: number
  version: number
  sourceUri?: string
  lastUsedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface SkillDetail {
  metadata: {
    skillId: string
    name: string
    description: string
    status: string
    source?: string
    version: number
    tags?: string[]
    pinned: boolean
    useCount: number
    viewCount: number
    patchCount: number
    createdAt?: string
    updatedAt?: string
    lastUsedAt?: string
  }
  content?: {
    name: string
    description: string
    body: string
    raw: string
  }
}

export interface SkillSuggestion {
  skillId: string
  name: string
  reason: string
}

export interface ConsolidationProposal {
  skillIdA: string
  skillIdB: string
  similarity: number
}

export interface CuratorReport {
  unused: SkillSuggestion[]
  narrow: SkillSuggestion[]
  similar: ConsolidationProposal[]
}

export interface ToolInfo {
  name: string
  description: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  enabledByDefault: boolean
}

export interface CharacterCard {
  id: string
  name: string
  description?: string
  firstMessage?: string
  tags?: string[]
}

export interface WeixinLoginState {
  loggedIn: boolean
  status: 'logged_in' | 'qr' | 'scaned' | 'expired' | 'idle' | 'error' | 'disabled'
  qrImage?: string | null
  message?: string | null
}

export interface MemoryItem {
  id: string
  userId: string
  characterId?: string
  category: string
  contentPreview: string
  confidence: number
  sourceUri?: string
  createdAt?: string
  updatedAt?: string
}
