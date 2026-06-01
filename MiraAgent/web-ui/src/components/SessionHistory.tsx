import { useState, type MouseEvent } from 'react'
import { listSessions, removeSession, type SessionMeta } from '../sessionStore'
import './SessionHistory.css'

interface Props {
  currentId: string
  onOpen: (id: string) => void
  onNewChat: () => void
}

function relTime(ts: number): string {
  const diff = Date.now() - ts
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 30) return `${d} 天前`
  return new Date(ts).toLocaleDateString('zh-CN')
}

export default function SessionHistory({ currentId, onOpen, onNewChat }: Props) {
  const [items, setItems] = useState<SessionMeta[]>(() => listSessions())

  function del(e: MouseEvent, id: string) {
    e.stopPropagation()
    removeSession(id)
    setItems(listSessions())
  }

  return (
    <div className="history glass">
      <header className="history-head">
        <div>
          <h1 className="history-title">会话记录</h1>
          <p className="history-sub">{items.length} 段对话 · 本地保存</p>
        </div>
        <button className="btn btn-accent" onClick={onNewChat}>新建对话</button>
      </header>

      <div className="history-scroll">
        {items.length === 0 ? (
          <div className="history-empty">
            <div className="empty-ring" />
            <p>还没有会话记录</p>
            <span>开始第一段对话，它会出现在这里。</span>
          </div>
        ) : (
          <div className="history-grid">
            {items.map((s) => (
              <button
                key={s.id}
                className={`sess-card ${s.id === currentId ? 'current' : ''}`}
                onClick={() => onOpen(s.id)}
              >
                <div className="sess-glyph">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                    <path d="M21 11.5a8.4 8.4 0 0 1-12.1 7.5L3 20.5l1.5-5.9A8.4 8.4 0 1 1 21 11.5Z" strokeLinejoin="round" />
                  </svg>
                </div>
                <div className="sess-body">
                  <div className="sess-title">{s.title}</div>
                  <div className="sess-meta">
                    <span>{relTime(s.updatedAt)}</span>
                    <span className="sess-id mono">{s.id}</span>
                  </div>
                </div>
                {s.id === currentId && <span className="sess-current pill">当前</span>}
                <span className="sess-del" onClick={(e) => del(e, s.id)} title="删除">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" width="15" height="15">
                    <path d="M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
