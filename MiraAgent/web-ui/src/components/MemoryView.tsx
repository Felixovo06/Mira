import { useEffect, useState } from 'react'
import { deleteMemory, getMemories } from '../api'
import type { MemoryItem } from '../types'
import './MemoryView.css'

interface Props {
  userId: string
}

const CAT_LABEL: Record<string, string> = {
  PROFILE: '档案',
  PREFERENCE: '偏好',
  GOAL: '目标',
  RELATIONSHIP: '关系',
  FACT: '事实',
}

export default function MemoryView({ userId }: Props) {
  const [items, setItems] = useState<MemoryItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  async function refresh() {
    setError(null)
    try {
      setItems(await getMemories(userId))
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId])

  async function del(m: MemoryItem) {
    if (!confirm(`删除这条记忆？\n[${m.category}] ${m.contentPreview}`)) return
    setBusy(m.id)
    try {
      await deleteMemory(m.id, userId)
      await refresh()
    } catch (e) {
      setError(String(e))
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="memory glass">
      <header className="memory-head">
        <div>
          <h1 className="memory-title">记忆</h1>
          <p className="memory-sub">
            {loading ? '加载中…' : `${items.length} 条长期记忆 · 用户 ${userId}`}
          </p>
        </div>
        <button className="btn" onClick={() => void refresh()}>刷新</button>
      </header>

      {error && <div className="memory-error">{error}</div>}

      <div className="memory-scroll">
        {!loading && items.length === 0 && (
          <div className="memory-empty">
            <div className="empty-ring" />
            <p>还没有长期记忆</p>
            <span>对话中 Agent 写入记忆后，会出现在这里。</span>
          </div>
        )}

        {items.map((m) => (
          <div key={m.id} className="mem-card">
            <div className="mem-top">
              <span className={`mem-cat cat-${m.category?.toLowerCase()}`}>
                {CAT_LABEL[m.category] ?? m.category}
              </span>
              <span className="mem-conf" title="置信度">conf {m.confidence}</span>
              <button
                className="mem-del"
                title="删除"
                onClick={() => void del(m)}
                disabled={busy === m.id}
              >
                {busy === m.id ? '…' : '删除'}
              </button>
            </div>
            <p className="mem-content">{m.contentPreview}</p>
            {m.sourceUri && <span className="mem-src mono">{m.sourceUri}</span>}
          </div>
        ))}
      </div>
    </div>
  )
}
