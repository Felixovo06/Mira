import type { TraceEvent } from '../types'
import './TracePanel.css'

interface Props {
  open: boolean
  traces: TraceEvent[]
  onClose: () => void
}

const TONE: Record<string, string> = {
  RUN_STARTED: 'start',
  PROMPT_BUILT: 'info',
  MODEL_REQUESTED: 'info',
  MODEL_RESPONDED: 'info',
  TOOL_CALL_PARSED: 'tool',
  TOOL_CALL_RECEIVED: 'tool',
  TOOL_EXECUTION_STARTED: 'tool',
  TOOL_EXECUTION_FINISHED: 'ok',
  TOOL_EXECUTION_FAILED: 'err',
  PERMISSION_DENIED: 'err',
  MEMORY_UPDATED: 'mem',
  SKILL_UPDATED: 'mem',
  CONTEXT_COMPRESSED: 'mem',
  SESSION_PERSISTED: 'ok',
  FINAL_RESPONSE: 'done',
  RUN_FAILED: 'err',
}

export default function TracePanel({ open, traces, onClose }: Props) {
  return (
    <>
      <div className={`trace-scrim ${open ? 'show' : ''}`} onClick={onClose} />
      <aside className={`trace-drawer glass ${open ? 'show' : ''}`}>
        <header className="trace-top">
          <div>
            <div className="trace-top-title">运行轨迹</div>
            <div className="trace-top-sub mono">{traces.length} events</div>
          </div>
          <button className="btn btn-ghost trace-close" onClick={onClose}>✕</button>
        </header>

        <div className="trace-list">
          {traces.length === 0 ? (
            <div className="trace-empty">发送消息后，这里会实时显示模型与工具的执行轨迹。</div>
          ) : (
            traces.map((ev, i) => (
              <div key={ev.id ?? i} className="trace-row" style={{ animationDelay: `${Math.min(i, 12) * 0.02}s` }}>
                <div className="trace-rail">
                  <span className={`trace-node ${TONE[ev.eventType] ?? 'info'}`} />
                  {i < traces.length - 1 && <span className="trace-line" />}
                </div>
                <div className="trace-content">
                  <div className="trace-row-head">
                    <span className="trace-type">{ev.eventType}</span>
                    <span className="trace-step mono">#{ev.stepIndex}</span>
                  </div>
                  {ev.payload && Object.keys(ev.payload).length > 0 && (
                    <pre className="trace-payload mono">{JSON.stringify(ev.payload, null, 1)}</pre>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </aside>
    </>
  )
}
