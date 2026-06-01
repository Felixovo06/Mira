import { useEffect, useState } from 'react'
import { getStyleConstraint, saveStyleConstraint } from '../api'
import type { StyleConstraint } from '../types'
import './StyleView.css'

const EMPTY: StyleConstraint = { enabled: true, worldSetting: '', tone: '', styleRules: [] }

export default function StyleView() {
  const [sc, setSc] = useState<StyleConstraint>(EMPTY)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => { void load() }, [])

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const d = await getStyleConstraint()
      setSc({
        enabled: d.enabled ?? true,
        worldSetting: d.worldSetting ?? '',
        tone: d.tone ?? '',
        styleRules: d.styleRules ?? [],
      })
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  function patch(p: Partial<StyleConstraint>) {
    setSc(s => ({ ...s, ...p }))
    setSaved(false)
  }

  const rules = sc.styleRules ?? []

  function setRule(i: number, v: string) {
    patch({ styleRules: rules.map((r, idx) => (idx === i ? v : r)) })
  }
  function addRule() {
    patch({ styleRules: [...rules, ''] })
  }
  function removeRule(i: number) {
    patch({ styleRules: rules.filter((_, idx) => idx !== i) })
  }

  async function save() {
    setBusy(true)
    setError(null)
    try {
      const cleaned: StyleConstraint = {
        ...sc,
        worldSetting: sc.worldSetting?.trim() ?? '',
        tone: sc.tone?.trim() ?? '',
        styleRules: rules.map(r => r.trim()).filter(Boolean),
      }
      const res = await saveStyleConstraint(cleaned)
      setSc({
        enabled: res.enabled ?? true,
        worldSetting: res.worldSetting ?? '',
        tone: res.tone ?? '',
        styleRules: res.styleRules ?? [],
      })
      setSaved(true)
    } catch (e) {
      setError(String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="style-view">
      <div className="style-head">
        <div>
          <div className="style-title">风格约束</div>
          <div className="style-sub">一份全局配置，对所有角色生效——规定世界设定与统一的回复语气、风格规则</div>
        </div>
        <label className="style-toggle" title="关闭后不再注入任何风格约束">
          <input
            type="checkbox"
            checked={sc.enabled}
            onChange={e => patch({ enabled: e.target.checked })}
          />
          <span className="style-toggle-track"><span className="style-toggle-thumb" /></span>
          <span className="style-toggle-label">{sc.enabled ? '已启用' : '已停用'}</span>
        </label>
      </div>

      {error && <div className="style-error">{error}</div>}

      {loading ? (
        <div className="style-loading">加载中…</div>
      ) : (
        <div className="style-body">
          <section className="style-section">
            <div className="style-section-label">世界设定</div>
            <div className="style-section-hint">大局背景、时空规则、世界观，约束所有角色所处的环境</div>
            <textarea
              className="style-input"
              rows={5}
              value={sc.worldSetting}
              onChange={e => patch({ worldSetting: e.target.value })}
              placeholder="例如：现实世界、贴近日常的陪伴场景，时间与现实同步……"
            />
          </section>

          <section className="style-section">
            <div className="style-section-label">回复语气</div>
            <div className="style-section-hint">口吻、人称、节奏，约束所有角色统一的说话方式</div>
            <textarea
              className="style-input"
              rows={3}
              value={sc.tone}
              onChange={e => patch({ tone: e.target.value })}
              placeholder="例如：自然口语化，像熟人聊天，句子偏短、有停顿感……"
            />
          </section>

          <section className="style-section">
            <div className="style-section-label">
              风格规则
              <span className="style-rule-count">{rules.length}</span>
            </div>
            <div className="style-section-hint">逐条硬性规则（禁用词、格式、长度等），每条独立生效</div>

            <div className="style-rules">
              {rules.length === 0 && (
                <div className="style-rules-empty">还没有规则，点下方「+ 添加规则」新增一条</div>
              )}
              {rules.map((rule, i) => (
                <div className="style-rule-row" key={i}>
                  <span className="style-rule-bullet">{i + 1}</span>
                  <input
                    className="style-input style-rule-input"
                    value={rule}
                    onChange={e => setRule(i, e.target.value)}
                    placeholder="例如：不使用'作为一个 AI'之类的免责声明"
                  />
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
              {busy ? '保存中…' : '保存并生效'}
            </button>
            {saved && <span className="style-saved">✓ 已保存，立即生效</span>}
          </div>
        </div>
      )}
    </div>
  )
}
