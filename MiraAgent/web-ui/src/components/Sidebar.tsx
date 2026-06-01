import type { ReactNode } from 'react'
import './Sidebar.css'

export type View = 'chat' | 'history' | 'memory' | 'skills' | 'wechat'

interface Props {
  view: View
  onView: (v: View) => void
  onNewChat: () => void
  userId: string
}

const NAV: { id: View; label: string; icon: ReactNode }[] = [
  {
    id: 'chat',
    label: '对话',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
        <path d="M21 11.5a8.4 8.4 0 0 1-12.1 7.5L3 20.5l1.5-5.9A8.4 8.4 0 1 1 21 11.5Z" strokeLinejoin="round" />
      </svg>
    ),
  },
  {
    id: 'history',
    label: '会话记录',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
        <path d="M3 12a9 9 0 1 0 3-6.7M3 4v4h4" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M12 8v4l3 2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    ),
  },
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
    id: 'wechat',
    label: '微信绑定',
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

export default function Sidebar({ view, onView, onNewChat, userId }: Props) {
  return (
    <aside className="sidebar glass">
      <div className="brand">
        <div className="brand-mark">
          <span />
        </div>
        <div className="brand-text">
          <div className="brand-name">Mira</div>
          <div className="brand-sub">companion agent</div>
        </div>
      </div>

      <button className="btn btn-accent new-chat" onClick={onNewChat}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
          <path d="M12 5v14M5 12h14" strokeLinecap="round" />
        </svg>
        新建对话
      </button>

      <nav className="nav">
        {NAV.map((n) => (
          <button
            key={n.id}
            className={`nav-item ${view === n.id ? 'active' : ''}`}
            onClick={() => onView(n.id)}
          >
            <span className="nav-icon">{n.icon}</span>
            <span className="nav-label">{n.label}</span>
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
