import { useEffect, useState } from 'react'
import { getCharacters, getCharacter, importCharacter } from '../api'
import type { CharacterCard } from '../types'
import './CharactersView.css'

type Mode = 'detail' | 'form' | 'json'

const EMPTY: CharacterCard = {
  id: '', name: '', description: '', personality: '', scenario: '',
  firstMessage: '', speakingStyle: '', relationshipToUser: '', systemNotes: '',
  exampleDialogues: [], tags: [],
}

export default function CharactersView() {
  const [list, setList] = useState<CharacterCard[]>([])
  const [selected, setSelected] = useState<CharacterCard | null>(null)
  const [mode, setMode] = useState<Mode>('detail')
  const [form, setForm] = useState<CharacterCard>(EMPTY)
  const [jsonText, setJsonText] = useState('')
  const [jsonError, setJsonError] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function refresh() {
    try {
      setList(await getCharacters())
    } catch (e) {
      setError(String(e))
    }
  }

  useEffect(() => { void refresh() }, [])

  async function open(id: string) {
    setError(null)
    setMode('detail')
    try {
      setSelected(await getCharacter(id))
    } catch (e) {
      setError(String(e))
    }
  }

  function startNew() {
    setSelected(null)
    setForm(EMPTY)
    setJsonText('')
    setJsonError('')
    setMode('form')
  }

  function setField(k: keyof CharacterCard, v: string) {
    setForm(f => ({ ...f, [k]: v }))
  }

  async function submitForm() {
    if (!form.id.trim() || !form.name.trim()) {
      setError('id 和 name 为必填项')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const saved = await importCharacter({
        ...form,
        tags: form.tags?.length ? form.tags : [],
        exampleDialogues: form.exampleDialogues?.length ? form.exampleDialogues : [],
      })
      await refresh()
      setSelected(saved)
      setMode('detail')
    } catch (e) {
      setError(String(e))
    } finally {
      setBusy(false)
    }
  }

  async function submitJson() {
    setJsonError('')
    let parsed: CharacterCard
    try {
      parsed = JSON.parse(jsonText)
    } catch {
      setJsonError('JSON 格式有误')
      return
    }
    if (!parsed.id || !parsed.name) {
      setJsonError('JSON 中 id 和 name 为必填项')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const saved = await importCharacter(parsed)
      await refresh()
      setSelected(saved)
      setMode('detail')
    } catch (e) {
      setError(String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="characters">
      <div className="characters-head">
        <div>
          <div className="characters-title">角色卡</div>
          <div className="characters-sub">管理 Agent 使用的角色人设</div>
        </div>
        <button className="btn btn-accent" onClick={startNew}>+ 导入角色卡</button>
      </div>

      {error && <div className="char-error">{error}</div>}

      <div className="characters-body">
        {/* 左侧列表 */}
        <div className="char-list">
          {list.length === 0 ? (
            <div className="char-empty">
              <p>暂无角色卡</p>
              <span>点击「导入角色卡」新建</span>
            </div>
          ) : list.map(c => (
            <button
              key={c.id}
              className={`char-card ${selected?.id === c.id && mode === 'detail' ? 'current' : ''}`}
              onClick={() => open(c.id)}
            >
              <div className="char-card-top">
                <span className="char-name">{c.name}</span>
                <span className="char-id">#{c.id}</span>
              </div>
              {c.description && <p className="char-desc">{c.description}</p>}
              {c.tags && c.tags.length > 0 && (
                <div className="char-tags">
                  {c.tags.map(t => <span key={t} className="tag">{t}</span>)}
                </div>
              )}
            </button>
          ))}
        </div>

        {/* 右侧面板 */}
        <div className="char-panel">
          {mode === 'detail' && selected && (
            <div className="char-detail">
              <div className="detail-head">
                <h2>{selected.name}</h2>
                <span className="detail-id">#{selected.id}</span>
              </div>
              {selected.tags && selected.tags.length > 0 && (
                <div className="char-tags" style={{ margin: '10px 0' }}>
                  {selected.tags.map(t => <span key={t} className="tag">{t}</span>)}
                </div>
              )}
              <div className="detail-fields">
                {selected.description && <Field label="描述" value={selected.description} />}
                {selected.personality && <Field label="性格" value={selected.personality} />}
                {selected.scenario && <Field label="场景设定" value={selected.scenario} />}
                {selected.speakingStyle && <Field label="说话风格" value={selected.speakingStyle} />}
                {selected.relationshipToUser && <Field label="与用户关系" value={selected.relationshipToUser} />}
                {selected.firstMessage && <Field label="开场白" value={selected.firstMessage} />}
                {selected.systemNotes && <Field label="系统备注" value={selected.systemNotes} />}
                {selected.exampleDialogues && selected.exampleDialogues.length > 0 && (
                  <div className="detail-field">
                    <div className="field-label">示例对话</div>
                    {selected.exampleDialogues.map((d, i) => (
                      <div key={i} className="field-value" style={{ marginBottom: 6 }}>{d}</div>
                    ))}
                  </div>
                )}
              </div>
              <button className="btn char-edit-btn" onClick={() => {
                setForm({ ...selected })
                setMode('form')
              }}>编辑</button>
            </div>
          )}

          {(mode === 'form' || mode === 'json') && (
            <div className="char-import">
              <div className="import-tabs">
                <button className={`import-tab ${mode === 'form' ? 'active' : ''}`} onClick={() => setMode('form')}>表单</button>
                <button className={`import-tab ${mode === 'json' ? 'active' : ''}`} onClick={() => setMode('json')}>JSON</button>
              </div>

              {mode === 'form' && (
                <div className="import-form">
                  <FormRow label="ID *" hint="唯一标识，字母数字">
                    <input className="char-input" value={form.id} onChange={e => setField('id', e.target.value)} placeholder="e.g. mira-v2" />
                  </FormRow>
                  <FormRow label="名字 *">
                    <input className="char-input" value={form.name} onChange={e => setField('name', e.target.value)} placeholder="角色名称" />
                  </FormRow>
                  <FormRow label="描述">
                    <textarea className="char-input" rows={2} value={form.description} onChange={e => setField('description', e.target.value)} placeholder="角色简介" />
                  </FormRow>
                  <FormRow label="性格">
                    <textarea className="char-input" rows={2} value={form.personality} onChange={e => setField('personality', e.target.value)} placeholder="性格特征" />
                  </FormRow>
                  <FormRow label="场景设定">
                    <textarea className="char-input" rows={2} value={form.scenario} onChange={e => setField('scenario', e.target.value)} placeholder="世界观/背景" />
                  </FormRow>
                  <FormRow label="说话风格">
                    <input className="char-input" value={form.speakingStyle} onChange={e => setField('speakingStyle', e.target.value)} placeholder="e.g. 温柔、简洁" />
                  </FormRow>
                  <FormRow label="与用户关系">
                    <input className="char-input" value={form.relationshipToUser} onChange={e => setField('relationshipToUser', e.target.value)} placeholder="e.g. 好友、助手" />
                  </FormRow>
                  <FormRow label="开场白">
                    <textarea className="char-input" rows={2} value={form.firstMessage} onChange={e => setField('firstMessage', e.target.value)} placeholder="第一条消息" />
                  </FormRow>
                  <FormRow label="系统备注">
                    <textarea className="char-input" rows={2} value={form.systemNotes} onChange={e => setField('systemNotes', e.target.value)} placeholder="对模型的额外指令" />
                  </FormRow>
                  <FormRow label="标签">
                    <input className="char-input" value={form.tags?.join(', ')} onChange={e => setForm(f => ({ ...f, tags: e.target.value.split(',').map(t => t.trim()).filter(Boolean) }))} placeholder="逗号分隔，e.g. 温柔, 幻想" />
                  </FormRow>
                  <button className="btn btn-accent import-submit" onClick={submitForm} disabled={busy}>
                    {busy ? '保存中…' : '保存角色卡'}
                  </button>
                </div>
              )}

              {mode === 'json' && (
                <div className="import-json">
                  <p className="json-hint">粘贴符合 CharacterProfile 结构的 JSON，id 和 name 为必填。</p>
                  <textarea
                    className="char-input json-area"
                    rows={16}
                    value={jsonText}
                    onChange={e => { setJsonText(e.target.value); setJsonError('') }}
                    placeholder={'{\n  "id": "mira-v2",\n  "name": "Mira",\n  "description": "...",\n  "personality": "..."\n}'}
                    spellCheck={false}
                  />
                  {jsonError && <div className="json-error">{jsonError}</div>}
                  <button className="btn btn-accent import-submit" onClick={submitJson} disabled={busy}>
                    {busy ? '导入中…' : '导入'}
                  </button>
                </div>
              )}
            </div>
          )}

          {mode === 'detail' && !selected && (
            <div className="char-placeholder">
              <p>选择左侧角色卡查看详情</p>
              <span>或点击「导入角色卡」新建</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="detail-field">
      <div className="field-label">{label}</div>
      <div className="field-value">{value}</div>
    </div>
  )
}

function FormRow({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="form-row">
      <label className="form-label">{label}{hint && <span className="form-hint"> — {hint}</span>}</label>
      {children}
    </div>
  )
}
