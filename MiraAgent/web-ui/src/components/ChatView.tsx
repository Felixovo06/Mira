import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import type { Message, StreamEvent, TraceEvent } from '../types'
import { getMessages, interrupt, streamChat } from '../api'
import { registerSession } from '../sessionStore'
import MessageBubble from './MessageBubble'
import ToolChip from './ToolChip'
import TracePanel from './TracePanel'
import './ChatView.css'

interface Props {
  sessionId: string
  userId: string
}

export default function ChatView({ sessionId, userId }: Props) {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [assistant, setAssistant] = useState('')
  const [traces, setTraces] = useState<TraceEvent[]>([])
  const [traceOpen, setTraceOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const runIdRef = useRef<string | null>(null)
  const abortRef = useRef<null | (() => void)>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const accRef = useRef('')

  useEffect(() => {
    setError(null)
    setAssistant('')
    setTraces([])
    getMessages(sessionId)
      .then((m) => setMessages(m.filter((x) => x.role !== 'system')))
      .catch(() => setMessages([]))
  }, [sessionId])

  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  }, [messages, assistant])

  function finalize(text: string) {
    if (text.trim()) {
      setMessages((m) => [
        ...m,
        { id: crypto.randomUUID(), role: 'assistant', content: text, createdAt: new Date().toISOString() },
      ])
    }
    setAssistant('')
    setStreaming(false)
    abortRef.current = null
  }

  function send() {
    const content = input.trim()
    if (!content || streaming) return
    setError(null)
    registerSession(sessionId, messages.length === 0 ? content : '')

    const userMsg: Message = {
      id: crypto.randomUUID(),
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    }
    setMessages((m) => [...m, userMsg])
    setInput('')
    setAssistant('')
    setStreaming(true)
    accRef.current = ''

    abortRef.current = streamChat(
      { userId, sessionId, content, enabledTools: ['note', 'todo'] },
      (ev: StreamEvent) => {
        if (ev.type === 'start') {
          runIdRef.current = ev.runId
        } else if (ev.type === 'text_delta') {
          accRef.current += ev.text
          setAssistant(accRef.current)
        } else if (ev.type === 'tool_call') {
          setMessages((m) => [
            ...m,
            {
              id: crypto.randomUUID(),
              role: 'tool',
              content: null,
              toolCallId: ev.toolCall.id,
              toolName: ev.toolCall.name,
              createdAt: new Date().toISOString(),
            },
          ])
        } else if (ev.type === 'tool_result') {
          setMessages((m) =>
            m.map((x) =>
              x.role === 'tool' && x.toolCallId === ev.toolResult.toolCallId
                ? { ...x, content: ev.toolResult.content, toolName: ev.toolResult.toolName }
                : x,
            ),
          )
        } else if (ev.type === 'trace') {
          setTraces((t) => [...t, ev.trace])
        } else if (ev.type === 'done') {
          finalize(ev.response.finalMessage?.content ?? accRef.current)
        } else if (ev.type === 'error') {
          setError(ev.message)
          setStreaming(false)
        }
      },
      (err) => {
        setError(err)
        setStreaming(false)
      },
    )
  }

  function stop() {
    abortRef.current?.()
    abortRef.current = null
    const rid = runIdRef.current
    if (rid) interrupt(rid).catch(() => {})
    finalize(accRef.current)
  }

  function onKey(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      send()
    }
  }

  const empty = messages.length === 0 && !streaming

  return (
    <div className="chat glass">
      <header className="chat-head">
        <div className="chat-head-l">
          <span className="chat-title">对话</span>
          <span className="chat-sid mono">{sessionId}</span>
        </div>
        <button
          className={`btn btn-ghost trace-btn ${traceOpen ? 'on' : ''}`}
          onClick={() => setTraceOpen((v) => !v)}
        >
          <span className="dot" style={{ background: traces.length ? 'var(--accent)' : 'var(--text-faint)', boxShadow: 'none' }} />
          Trace
          {traces.length > 0 && <span className="trace-count">{traces.length}</span>}
        </button>
      </header>

      <div className="chat-scroll" ref={scrollRef}>
        {empty ? (
          <div className="chat-empty">
            <div className="empty-orb" />
            <h2>开始一段对话</h2>
            <p>这是有状态、可调用工具的陪伴型 Agent。试着让它帮你记一条 todo。</p>
            <div className="empty-hints">
              <span className="pill">记一条待办：明天读论文</span>
              <span className="pill">你还记得我说过什么吗</span>
            </div>
          </div>
        ) : (
          <div className="chat-thread">
            {messages.map((m) => {
              if (m.role === 'tool') return <ToolChip key={m.id} name={m.toolName ?? 'tool'} content={m.content} />
              if (m.role === 'system') return null
              return <MessageBubble key={m.id} role={m.role} content={m.content ?? ''} />
            })}
            {streaming && (
              <MessageBubble role="assistant" content={assistant} pending />
            )}
          </div>
        )}
      </div>

      {error && <div className="chat-error">⚠ {error}</div>}

      <div className="composer">
        <textarea
          className="composer-input"
          placeholder="说点什么…（Enter 发送，Shift+Enter 换行）"
          value={input}
          rows={1}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKey}
        />
        {streaming ? (
          <button className="btn btn-danger send-btn" onClick={stop}>停止</button>
        ) : (
          <button className="btn btn-accent send-btn" onClick={send} disabled={!input.trim()}>
            发送
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
              <path d="M5 12h14M13 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        )}
      </div>

      <TracePanel open={traceOpen} traces={traces} onClose={() => setTraceOpen(false)} />
    </div>
  )
}
