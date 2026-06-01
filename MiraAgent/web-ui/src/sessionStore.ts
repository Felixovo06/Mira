// 前端会话记录：后端暂无「列出用户所有会话」接口，用 localStorage 记录会话清单，
// 待后端补 GET /api/sessions?userId=... 后可平滑替换此模块。

export interface SessionMeta {
  id: string
  title: string
  createdAt: number
  updatedAt: number
}

const USER_KEY = 'mira_user_id'
const CUR_KEY = 'mira_current_session'
const LIST_KEY = 'mira_sessions'

function uid(prefix: string): string {
  const c = globalThis.crypto
  if (c && typeof c.randomUUID === 'function') return prefix + c.randomUUID().slice(0, 8)
  return prefix + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

export function ensureUserId(): string {
  let id = localStorage.getItem(USER_KEY)
  if (!id) {
    id = uid('u_')
    localStorage.setItem(USER_KEY, id)
  }
  return id
}

export function newSessionId(): string {
  const id = uid('s_')
  setCurrentSessionId(id)
  return id
}

export function getCurrentSessionId(): string | null {
  return localStorage.getItem(CUR_KEY)
}

export function setCurrentSessionId(id: string): void {
  localStorage.setItem(CUR_KEY, id)
}

export function listSessions(): SessionMeta[] {
  try {
    const raw = JSON.parse(localStorage.getItem(LIST_KEY) ?? '[]') as SessionMeta[]
    return raw.sort((a, b) => b.updatedAt - a.updatedAt)
  } catch {
    return []
  }
}

function saveAll(list: SessionMeta[]): void {
  localStorage.setItem(LIST_KEY, JSON.stringify(list))
}

function clip(s: string): string {
  const t = s.trim().replace(/\s+/g, ' ')
  return t.length > 30 ? t.slice(0, 30) + '…' : t
}

export function registerSession(id: string, titleSeed: string): void {
  const list = listSessions()
  const now = Date.now()
  const existing = list.find((s) => s.id === id)
  if (existing) {
    existing.updatedAt = now
    if ((!existing.title || existing.title === '新对话') && titleSeed) existing.title = clip(titleSeed)
  } else {
    list.push({ id, title: titleSeed ? clip(titleSeed) : '新对话', createdAt: now, updatedAt: now })
  }
  saveAll(list)
}

export function removeSession(id: string): void {
  saveAll(listSessions().filter((s) => s.id !== id))
}
