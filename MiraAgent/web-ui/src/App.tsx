import { useState } from 'react'
import Sidebar, { type View } from './components/Sidebar'
import ChatView from './components/ChatView'
import CharacterSelect from './components/CharacterSelect'
import MemoryView from './components/MemoryView'
import DocumentsView from './components/DocumentsView'
import SkillsView from './components/SkillsView'
import CharactersView from './components/CharactersView'
import EvalDashboard from './components/EvalDashboard'
import WechatBinding from './components/WechatBinding'
import { ensureUserId, getCurrentSessionId, listSessions, newSessionId, setCurrentSessionId } from './sessionStore'
import './App.css'

export default function App() {
  const [userId] = useState<string>(ensureUserId)

  const [sessionId, setSessionId] = useState<string>(() => {
    const cur = getCurrentSessionId()
    if (cur) return cur
    const sessions = listSessions()
    if (sessions.length > 0) return sessions[0].id
    return newSessionId()
  })

  const [characterId, setCharacterId] = useState<string>(() => {
    const sessions = listSessions()
    return sessions[0]?.characterId ?? 'mira'
  })

  const [view, setView] = useState<View>(() => {
    const cur = getCurrentSessionId()
    const sessions = listSessions()
    return (cur || sessions.length > 0) ? 'chat' : 'character-select'
  })

  function startNewChat() {
    setView('character-select')
  }

  function onCharacterSelected(cid: string) {
    const id = newSessionId()
    setCurrentSessionId(id)
    setSessionId(id)
    setCharacterId(cid)
    setView('chat')
  }

  function openSession(id: string, cid?: string) {
    setCurrentSessionId(id)
    setSessionId(id)
    if (cid) setCharacterId(cid)
    setView('chat')
  }

  return (
    <>
      <div className="aurora" />
      <div className="grain" />
      <div className="vignette" />

      <div className="app">
        <Sidebar
          view={view}
          onView={(v) => setView(v)}
          onNewChat={startNewChat}
          onSession={openSession}
          sessionId={sessionId}
          userId={userId}
        />

        <main className="stage">
          {view === 'character-select' && (
            <CharacterSelect onSelect={onCharacterSelected} />
          )}
          {view === 'chat' && (
            <ChatView key={sessionId} sessionId={sessionId} userId={userId} characterId={characterId} />
          )}
          {view === 'memory' && <MemoryView userId={userId} />}
          {view === 'documents' && <DocumentsView />}
          {view === 'skills' && <SkillsView />}
          {view === 'characters' && <CharactersView />}
          {view === 'eval' && <EvalDashboard />}
          {view === 'wechat' && <WechatBinding userId={userId} />}
        </main>
      </div>
    </>
  )
}
