import { useState } from 'react'
import Sidebar, { type View } from './components/Sidebar'
import ChatView from './components/ChatView'
import SessionHistory from './components/SessionHistory'
import MemoryView from './components/MemoryView'
import SkillsView from './components/SkillsView'
import WechatBinding from './components/WechatBinding'
import { ensureUserId, getCurrentSessionId, newSessionId, setCurrentSessionId } from './sessionStore'
import './App.css'

export default function App() {
  const [view, setView] = useState<View>('chat')
  const [userId] = useState<string>(ensureUserId)
  const [sessionId, setSessionId] = useState<string>(() => getCurrentSessionId() ?? newSessionId())

  function startNewChat() {
    setSessionId(newSessionId())
    setView('chat')
  }

  function openSession(id: string) {
    setCurrentSessionId(id)
    setSessionId(id)
    setView('chat')
  }

  return (
    <>
      <div className="aurora" />
      <div className="grain" />
      <div className="vignette" />

      <div className="app">
        <Sidebar view={view} onView={setView} onNewChat={startNewChat} userId={userId} />

        <main className="stage">
          {view === 'chat' && <ChatView key={sessionId} sessionId={sessionId} userId={userId} />}
          {view === 'history' && (
            <SessionHistory currentId={sessionId} onOpen={openSession} onNewChat={startNewChat} />
          )}
          {view === 'memory' && <MemoryView userId={userId} />}
          {view === 'skills' && <SkillsView />}
          {view === 'wechat' && <WechatBinding userId={userId} />}
        </main>
      </div>
    </>
  )
}
