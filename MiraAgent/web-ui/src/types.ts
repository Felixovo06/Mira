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
  content: string | null
  status: string
  toolExecutions: ToolExecution[]
  usage?: { inputTokens: number; outputTokens: number }
  error?: string
}

export interface TraceEvent {
  id: string
  runId: string
  sessionId: string
  stepIndex: number
  eventType: string
  payload: Record<string, unknown>
  createdAt: string
}
