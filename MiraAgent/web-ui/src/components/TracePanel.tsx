import { useEffect, useState } from 'react'
import { getTrace } from '../api'
import type { TraceEvent } from '../types'
import './TracePanel.css'

interface Props {
  runId: string | null
}

const EVENT_ICONS: Record<string, string> = {
  RUN_STARTED: '▶',
  PROMPT_BUILT: '📝',
  MODEL_REQUESTED: '⬆',
  MODEL_RESPONDED: '⬇',
  TOOL_CALL_RECEIVED: '🔧',
  TOOL_EXECUTION_FINISHED: '✓',
  FINAL_RESPONSE: '✅',
  SESSION_PERSISTED: '💾',
  RUN_FAILED: '✗',
}

export function TracePanel({ runId }: Props) {
  const [events, setEvents] = useState<TraceEvent[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!runId) {
      setEvents([])
      return
    }
    setLoading(true)
    setError(null)
    getTrace(runId)
      .then(setEvents)
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false))
  }, [runId])

  return (
    <div className="trace-panel">
      <div className="trace-header">
        <span>Trace</span>
        {runId && <span className="trace-run-id">{runId.slice(0, 8)}…</span>}
      </div>
      <div className="trace-body">
        {!runId && (
          <div className="trace-empty">发送消息后查看 Trace</div>
        )}
        {loading && <div className="trace-empty">Loading…</div>}
        {error && <div className="trace-empty trace-error">{error}</div>}
        {!loading &&
          events.map((ev) => (
            <div key={ev.id} className="trace-event">
              <span className="trace-step">#{ev.stepIndex}</span>
              <span className="trace-icon">
                {EVENT_ICONS[ev.eventType] ?? '•'}
              </span>
              <span className="trace-type">{ev.eventType}</span>
              {Object.keys(ev.payload).length > 0 && (
                <details className="trace-payload">
                  <summary>payload</summary>
                  <pre>{JSON.stringify(ev.payload, null, 2)}</pre>
                </details>
              )}
            </div>
          ))}
      </div>
    </div>
  )
}
