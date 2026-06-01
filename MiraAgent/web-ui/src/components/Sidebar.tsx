import { type MouseEvent, type ReactNode, useState, useEffect } from 'react'
import { listSessions, removeSession, type SessionMeta } from '../sessionStore'
import './Sidebar.css'

export type View = 'chat' | 'character-select' | 'memory' | 'documents' | 'skills' | 'characters' | 'eval' | 'wechat'

type ToolView = 'memory' | 'documents' | 'skills' | 'characters' | 'eval' | 'wechat'

interface Props {
  view: View
  onView: (v: ToolView) => void
  onNewChat: () => void
  onSession: (id: string, characterId?: string) => void
  sessionId: string
  userId: string
}

const TOOL_NAV: { id: ToolView; label: string; icon: ReactNode }[] = [
  {
    id: 'memory',
    label: '记忆',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
        <path d="M12 3a4 4 0 0 0-4 4v1a3 3 0 0 0 0 6 4 4 0 0 0 8 0 3 3 0 0 0 0-6V7a4 4 0 0 0-4-4Z" strokeLinejoin="round" />
        <path d="M12 3v18" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    id: 'skills',
    label: '技能库',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
        <path d="M12 2 3 7l9 5 9-5-9-5Z" strokeLinejoin="round" />
        <path d="m3 12 9 5 9-5M3 17l9 5 9-5" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    ),
  },
  {
    id: 'documents',
    label: '文档',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
        <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z" strokeLinejoin="round" />
        <path d="M14 3v5h5M9 13h6M9 17h6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    ),
  },
  {
    id: 'characters',
    label: '角色卡',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
        <circle cx="12" cy="8" r="4" />
        <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" strokeLinecap="round" />
        <path d="M18 3l1.5 1.5L22 2M18 7h4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    ),
  },
  {
    id: 'eval',
    label: '评测',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
        <path d="M4 19V5M4 19h16" strokeLinecap="round" />
        <rect x="7" y="11" width="3" height="5" rx="0.5" />
        <rect x="12" y="8" width="3" height="8" rx="0.5" />
        <rect x="17" y="13" width="3" height="3" rx="0.5" />
      </svg>
    ),
  },
  {
    id: 'wechat',
    label: '微信',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
        <rect x="3" y="3" width="7" height="7" rx="1.4" />
        <rect x="14" y="3" width="7" height="7" rx="1.4" />
        <rect x="3" y="14" width="7" height="7" rx="1.4" />
        <path d="M14 14h3v3M21 14v.01M14 21h.01M21 18v3h-3" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    ),
  },
]

function relTime(ts: number): string {
  const diff = Date.now() - ts
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m}m`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h`
  return `${Math.floor(h / 24)}d`
}

export default function Sidebar({ view, onView, onNewChat, onSession, sessionId, userId }: Props) {
  const [sessions, setSessions] = useState<SessionMeta[]>(() => listSessions())

  useEffect(() => {
    const id = setInterval(() => setSessions(listSessions()), 3000)
    return () => clearInterval(id)
  }, [])

  function del(e: MouseEvent, id: string) {
    e.stopPropagation()
    removeSession(id)
    setSessions(listSessions())
  }

  return (
    <aside className="sidebar glass">
      {/* Brand */}
      <div className="brand">
        <div className="brand-mark">
          <img src="/mira-logo.png" alt="Mira" className="brand-logo" />
        </div>
        <div className="brand-text">
          <div className="brand-name">Mira</div>
          <div className="brand-sub">companion agent</div>
        </div>
      </div>

      {/* New Chat */}
      <button className="btn btn-accent new-chat" onClick={onNewChat}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15">
          <path d="M12 5v14M5 12h14" strokeLinecap="round" />
        </svg>
        新建对话
      </button>

      {/* Session List */}
      <div className="sess-list">
        {sessions.length === 0 ? (
          <div className="sess-empty">还没有会话</div>
        ) : (
          sessions.map((s) => (
            <button
              key={s.id}
              className={`sess-row ${s.id === sessionId && (view === 'chat') ? 'active' : ''}`}
              onClick={() => onSession(s.id, s.characterId)}
            >
              <div className="sess-row-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
                  <path d="M21 11.5a8.4 8.4 0 0 1-12.1 7.5L3 20.5l1.5-5.9A8.4 8.4 0 1 1 21 11.5Z" strokeLinejoin="round" />
                </svg>
              </div>
              <div className="sess-row-body">
                <span className="sess-row-title">{s.title}</span>
                <span className="sess-row-time">{relTime(s.updatedAt)}</span>
              </div>
              <span className="sess-row-del" onClick={(e) => del(e, s.id)} title="删除">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="14" height="14">
                  <path d="M18 6 6 18M6 6l12 12" strokeLinecap="round" />
                </svg>
              </span>
            </button>
          ))
        )}
      </div>

      {/* Bottom Tool Nav */}
      <nav className="tool-nav">
        {TOOL_NAV.map((n) => (
          <button
            key={n.id}
            className={`tool-nav-item ${view === n.id ? 'active' : ''}`}
            onClick={() => onView(n.id)}
            title={n.label}
          >
            <span className="tool-nav-icon">{n.icon}</span>
            <span className="tool-nav-label">{n.label}</span>
            {view === n.id && <span className="nav-marker" />}
          </button>
        ))}
      </nav>

      <div className="side-foot">
        <div className="pill">
          <span className="dot" />
          在线
        </div>
        <div className="uid mono" title={userId}>{userId}</div>
      </div>
    </aside>
  )
}
