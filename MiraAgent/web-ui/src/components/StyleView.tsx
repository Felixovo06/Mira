import { useEffect, useState } from 'react'
import {
  listWorldBook,
  upsertWorldBookEntry,
  deleteWorldBookEntry,
  toggleWorldBookEntry,
} from '../api'
import type { StyleConstraint } from '../types'
import './StyleView.css'

const NEW = '__new__'

function emptyEntry(): StyleConstraint {
  return { name: '', enabled: true, worldSetting: '', tone: '', styleRules: [] }
}

export default function StyleView() {
  const [entries, setEntries] = useState<StyleConstraint[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [openId, setOpenId] = useState<string | null>(null) // 条目 id 或 NEW
  const [form, setForm] = useState<StyleConstraint | null>(null)
  const [busy, setBusy] = useState(false)
  const [savedFlash, setSavedFlash] = useState(false)

  useEffect(() => { void load() }, [])

  async function load() {
    setLoading(true); setError(null)
    try {
      setEntries(await listWorldBook())
    } catch (e) { setError(String(e)) } finally { setLoading(false) }
  }

  function openEntry(e: StyleConstraint) {
    setSavedFlash(false)
    setOpenId(e.id ?? null)
    setForm({ ...e, styleRules: [...(e.styleRules ?? [])] })
  }
  function openNew() {
    setSavedFlash(false)
    setOpenId(NEW)
    setForm(emptyEntry())
  }
  function closeEditor() { setOpenId(null); setForm(null) }

  function patch(p: Partial<StyleConstraint>) {
    setForm(f => (f ? { ...f, ...p } : f))
    setSavedFlash(false)
  }

  const rules = form?.styleRules ?? []
  const setRule = (i: number, v: string) => patch({ styleRules: rules.map((r, idx) => (idx === i ? v : r)) })
  const addRule = () => patch({ styleRules: [...rules, ''] })
  const removeRule = (i: number) => patch({ styleRules: rules.filter((_, idx) => idx !== i) })

  async function save() {
    if (!form) return
    setBusy(true); setError(null)
    try {
      const cleaned: StyleConstraint = {
        ...form,
        name: form.name?.trim() || '未命名条目',
        worldSetting: form.worldSetting?.trim() ?? '',
        tone: form.tone?.trim() ?? '',
        styleRules: rules.map(r => r.trim()).filter(Boolean),
      }
      const saved = await upsertWorldBookEntry(cleaned)
      await load()
      setOpenId(saved.id ?? null)
      setForm({ ...saved, styleRules: [...(saved.styleRules ?? [])] })
      setSavedFlash(true)
    } catch (e) { setError(String(e)) } finally { setBusy(false) }
  }

  async function toggle(e: StyleConstraint, on: boolean) {
    if (!e.id) return
    try {
      const updated = await toggleWorldBookEntry(e.id, on)
      setEntries(list => list.map(x => (x.id === e.id ? updated : x)))
      if (openId === e.id) setForm(f => (f ? { ...f, enabled: on } : f))
    } catch (err) { setError(String(err)) }
  }

  async function remove(e: StyleConstraint) {
    if (!e.id) return
    if (!window.confirm(`删除条目「${e.name || '未命名条目'}」？`)) return
    try {
      await deleteWorldBookEntry(e.id)
      if (openId === e.id) closeEditor()
      await load()
    } catch (err) { setError(String(err)) }
  }

  function summary(e: StyleConstraint): string {
    const s = (e.worldSetting || e.tone || (e.styleRules ?? []).join('；') || '').replace(/\s+/g, ' ').trim()
    return s.length > 64 ? s.slice(0, 64) + '…' : s || '（空条目）'
  }

  const enabledCount = entries.filter(e => e.enabled).length

  function renderEditor(isNew: boolean) {
    if (!form) return null
    return (
      <div className="wb-editor">
        <section className="style-section">
          <div className="style-section-label">条目名称</div>
          <input
            className="style-input"
            value={form.name ?? ''}
            onChange={e => patch({ name: e.target.value })}
            placeholder="例如：现实日常世界 / 毒舌语气 / 中二设定"
          />
        </section>

        <section className="style-section">
          <div className="style-section-label">世界设定</div>
          <div className="style-section-hint">大局背景、时空规则、世界观，约束所有角色所处的环境</div>
          <textarea className="style-input" rows={4} value={form.worldSetting ?? ''}
            onChange={e => patch({ worldSetting: e.target.value })}
            placeholder="例如：现实世界、贴近日常的陪伴场景，时间与现实同步……" />
        </section>

        <section className="style-section">
          <div className="style-section-label">回复语气</div>
          <div className="style-section-hint">口吻、人称、节奏，约束所有角色统一的说话方式</div>
          <textarea className="style-input" rows={3} value={form.tone ?? ''}
            onChange={e => patch({ tone: e.target.value })}
            placeholder="例如：自然口语化，像熟人聊天，句子偏短、有停顿感……" />
        </section>

        <section className="style-section">
          <div className="style-section-label">风格规则<span className="style-rule-count">{rules.length}</span></div>
          <div className="style-section-hint">逐条硬性规则（禁用词、格式、长度等），每条独立生效</div>
          <div className="style-rules">
            {rules.length === 0 && <div className="style-rules-empty">还没有规则，点下方「+ 添加规则」新增一条</div>}
            {rules.map((rule, i) => (
              <div className="style-rule-row" key={i}>
                <span className="style-rule-bullet">{i + 1}</span>
                <input className="style-input style-rule-input" value={rule}
                  onChange={e => setRule(i, e.target.value)}
                  placeholder="例如：不使用'作为一个 AI'之类的免责声明" />
                <button className="style-rule-del" onClick={() => removeRule(i)} title="删除该规则">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="15" height="15">
                    <path d="M18 6 6 18M6 6l12 12" strokeLinecap="round" />
                  </svg>
                </button>
              </div>
            ))}
          </div>
          <button className="style-add-rule" onClick={addRule}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15">
              <path d="M12 5v14M5 12h14" strokeLinecap="round" />
            </svg>
            添加规则
          </button>
        </section>

        <div className="style-actions">
          <button className="btn btn-accent style-save" onClick={save} disabled={busy}>
            {busy ? '保存中…' : isNew ? '创建条目' : '保存并生效'}
          </button>
          <button className="btn wb-cancel" onClick={closeEditor} disabled={busy}>{isNew ? '取消' : '收起'}</button>
          {savedFlash && <span className="style-saved">✓ 已保存，立即生效</span>}
        </div>
      </div>
    )
  }

  return (
    <div className="style-view">
      <div className="style-head">
        <div>
          <div className="style-title">风格</div>
          <div className="style-sub">
            插拔式条目，每条可单独开关——启用的条目按顺序拼进<b>所有角色</b>的系统提示词最前端，规定世界设定与统一语气/规则
          </div>
        </div>
        <button className="btn btn-accent wb-new-btn" onClick={openNew}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15">
            <path d="M12 5v14M5 12h14" strokeLinecap="round" />
          </svg>
          新建条目
        </button>
      </div>

      {error && <div className="style-error">{error}</div>}

      {loading ? (
        <div className="style-loading">加载中…</div>
      ) : (
        <div className="wb-list">
          <div className="wb-stat">共 {entries.length} 条 · {enabledCount} 条启用</div>

          {openId === NEW && form && (
            <div className="wb-card wb-card-open">{renderEditor(true)}</div>
          )}

          {entries.length === 0 && openId !== NEW && (
            <div className="wb-empty">还没有任何世界书条目，点右上角「新建条目」开始。</div>
          )}

          {entries.map(e => {
            const open = openId === e.id
            return (
              <div className={`wb-card${open ? ' wb-card-open' : ''}${e.enabled ? '' : ' wb-card-off'}`} key={e.id}>
                <div className="wb-card-head" onClick={() => (open ? closeEditor() : openEntry(e))}>
                  <label className="style-toggle wb-card-toggle" onClick={ev => ev.stopPropagation()} title={e.enabled ? '已启用' : '已停用'}>
                    <input type="checkbox" checked={e.enabled} onChange={ev => toggle(e, ev.target.checked)} />
                    <span className="style-toggle-track"><span className="style-toggle-thumb" /></span>
                  </label>
                  <div className="wb-card-main">
                    <div className="wb-card-name">{e.name || '未命名条目'}</div>
                    <div className="wb-card-summary">{summary(e)}</div>
                  </div>
                  <button className="wb-icon-btn wb-del" onClick={ev => { ev.stopPropagation(); void remove(e) }} title="删除条目">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="15" height="15">
                      <path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </button>
                  <span className={`wb-chevron${open ? ' open' : ''}`}>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                      <path d="M6 9l6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </span>
                </div>
                {open && form && renderEditor(false)}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
