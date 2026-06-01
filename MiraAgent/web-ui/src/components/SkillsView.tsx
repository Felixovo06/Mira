import { useEffect, useState, type MouseEvent } from 'react'
import { archiveSkill, getCuratorReport, getSkill, getSkills, pinSkill } from '../api'
import type { CuratorReport, SkillDetail, SkillIndex } from '../types'
import './SkillsView.css'

export default function SkillsView() {
  const [skills, setSkills] = useState<SkillIndex[]>([])
  const [report, setReport] = useState<CuratorReport | null>(null)
  const [selected, setSelected] = useState<SkillDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  async function refresh() {
    setError(null)
    try {
      const [list, rep] = await Promise.all([getSkills(), getCuratorReport().catch(() => null)])
      setSkills(list)
      setReport(rep)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
  }, [])

  async function open(id: string) {
    try {
      setSelected(await getSkill(id))
    } catch (e) {
      setError(String(e))
    }
  }

  async function togglePin(e: MouseEvent, s: SkillIndex) {
    e.stopPropagation()
    setBusy(s.skillId)
    try {
      await pinSkill(s.skillId, !s.pinned)
      await refresh()
    } catch (err) {
      setError(String(err))
    } finally {
      setBusy(null)
    }
  }

  async function archive(e: MouseEvent, s: SkillIndex) {
    e.stopPropagation()
    if (!confirm(`归档技能「${s.name}」？`)) return
    setBusy(s.skillId)
    try {
      await archiveSkill(s.skillId)
      if (selected?.metadata.skillId === s.skillId) setSelected(null)
      await refresh()
    } catch (err) {
      setError(String(err))
    } finally {
      setBusy(null)
    }
  }

  const suggestionCount =
    (report?.unused.length ?? 0) + (report?.narrow.length ?? 0) + (report?.similar.length ?? 0)

  return (
    <div className="skills glass">
      <header className="skills-head">
        <div>
          <h1 className="skills-title">技能库</h1>
          <p className="skills-sub">
            {loading ? '加载中…' : `${skills.length} 个活跃技能 · Agent 在对话中沉淀与复用`}
          </p>
        </div>
        <button className="btn" onClick={() => void refresh()}>刷新</button>
      </header>

      {error && <div className="skills-error">{error}</div>}

      <div className="skills-body">
        <div className="skills-list">
          {!loading && skills.length === 0 && (
            <div className="skills-empty">
              <div className="empty-ring" />
              <p>还没有技能</p>
              <span>对话中触发后台复盘后，学到的技能会出现在这里。</span>
            </div>
          )}

          {skills.map((s) => (
            <button
              key={s.skillId}
              className={`skill-card ${selected?.metadata.skillId === s.skillId ? 'current' : ''}`}
              onClick={() => void open(s.skillId)}
            >
              <div className="skill-card-top">
                <span className="skill-name">{s.name}</span>
                {s.pinned && <span className="pill pin-pill">📌 置顶</span>}
              </div>
              <p className="skill-desc">{s.description}</p>
              <div className="skill-meta">
                <span className="mono">v{s.version}</span>
                <span>用过 {s.useCount} 次</span>
                {s.tags?.slice(0, 3).map((t) => (
                  <span key={t} className="tag">{t}</span>
                ))}
              </div>
              <div className="skill-actions">
                <span
                  className="skill-act"
                  title={s.pinned ? '取消置顶' : '置顶'}
                  onClick={(e) => void togglePin(e, s)}
                >
                  {busy === s.skillId ? '…' : s.pinned ? '取消置顶' : '置顶'}
                </span>
                <span
                  className="skill-act danger"
                  title="归档"
                  onClick={(e) => void archive(e, s)}
                >
                  归档
                </span>
              </div>
            </button>
          ))}
        </div>

        <div className="skills-side">
          {selected ? (
            <div className="skill-detail">
              <div className="detail-head">
                <h2>{selected.metadata.name}</h2>
                <span className="mono detail-id">{selected.metadata.skillId}</span>
              </div>
              <div className="detail-stats">
                <span>v{selected.metadata.version}</span>
                <span>用 {selected.metadata.useCount}</span>
                <span>看 {selected.metadata.viewCount}</span>
                <span>改 {selected.metadata.patchCount}</span>
                {selected.metadata.source && <span className="tag">{selected.metadata.source}</span>}
              </div>
              <pre className="detail-body">{selected.content?.body || selected.content?.raw || '(无正文)'}</pre>
            </div>
          ) : (
            <div className="curator">
              <h2 className="curator-title">Curator 建议</h2>
              <p className="curator-sub">
                {suggestionCount === 0 ? '暂无优化建议' : `${suggestionCount} 条优化建议`}
              </p>
              <CuratorBlock title="长期未使用，建议归档" items={report?.unused} />
              <CuratorBlock title="使用过少，建议收窄/合并" items={report?.narrow} />
              {report?.similar && report.similar.length > 0 && (
                <div className="curator-group">
                  <div className="curator-group-title">疑似重复，建议合并</div>
                  {report.similar.map((p, i) => (
                    <div key={i} className="curator-item">
                      <span className="mono">{p.skillIdA}</span> ↔ <span className="mono">{p.skillIdB}</span>
                      <span className="sim">{(p.similarity * 100).toFixed(0)}%</span>
                    </div>
                  ))}
                </div>
              )}
              <p className="curator-hint">点击左侧技能查看 SKILL.md 正文。</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function CuratorBlock({ title, items }: { title: string; items?: { skillId: string; name: string; reason: string }[] }) {
  if (!items || items.length === 0) return null
  return (
    <div className="curator-group">
      <div className="curator-group-title">{title}</div>
      {items.map((s) => (
        <div key={s.skillId} className="curator-item">
          <span className="curator-name">{s.name}</span>
          <span className="curator-reason">{s.reason}</span>
        </div>
      ))}
    </div>
  )
}
