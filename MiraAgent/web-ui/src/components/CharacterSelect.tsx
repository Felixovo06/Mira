import { useEffect, useState } from 'react'
import { getCharacters } from '../api'
import type { CharacterCard } from '../types'
import './CharacterSelect.css'

interface Props {
  onSelect: (characterId: string) => void
}

export default function CharacterSelect({ onSelect }: Props) {
  const [characters, setCharacters] = useState<CharacterCard[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getCharacters()
      .then(setCharacters)
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="char-select-screen glass">
      <div className="char-select-hero">
        <img src="/mira-logo.png" alt="Mira" className="char-select-logo" />
        <h1 className="char-select-title">选择角色</h1>
        <p className="char-select-sub">选择一个角色开始对话</p>
      </div>

      <div className="char-select-grid">
        {loading && <div className="char-select-loading">加载中…</div>}
        {!loading && characters.length === 0 && (
          <div className="char-select-empty">
            <p>暂无角色卡</p>
            <span>前往「角色卡」页面导入角色</span>
          </div>
        )}
        {characters.map((c) => (
          <button key={c.id} className="char-pick-card" onClick={() => onSelect(c.id)}>
            <div className="char-pick-avatar">
              {c.name.slice(0, 1)}
            </div>
            <div className="char-pick-info">
              <div className="char-pick-name">{c.name}</div>
              {c.description && <div className="char-pick-desc">{c.description}</div>}
              {c.tags && c.tags.length > 0 && (
                <div className="char-pick-tags">
                  {c.tags.slice(0, 3).map(t => <span key={t} className="tag">{t}</span>)}
                </div>
              )}
            </div>
            <svg className="char-pick-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
              <path d="M9 18l6-6-6-6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        ))}
      </div>
    </div>
  )
}
