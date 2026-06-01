import { useState } from 'react'
import { Chat } from './components/Chat'
import { TracePanel } from './components/TracePanel'
import './App.css'

export default function App() {
  const [runId, setRunId] = useState<string | null>(null)
  const [showTrace, setShowTrace] = useState(false)

  return (
    <div className="app">
      <header className="app-header">
        <span className="app-title">MiraAgent</span>
        <button
          className={`trace-toggle ${showTrace ? 'active' : ''}`}
          onClick={() => setShowTrace((v) => !v)}
          title="Toggle trace panel"
        >
          Trace {showTrace ? '▶' : '◀'}
        </button>
      </header>
      <div className="app-body">
        <Chat onRunComplete={setRunId} />
        {showTrace && <TracePanel runId={runId} />}
      </div>
    </div>
  )
}
