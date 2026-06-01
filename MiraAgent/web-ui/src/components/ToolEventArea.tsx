import type { ToolExecution } from '../types'
import './ToolEventArea.css'

interface Props {
  executions: ToolExecution[]
}

export function ToolEventArea({ executions }: Props) {
  if (executions.length === 0) return null

  return (
    <div className="tool-area">
      <div className="tool-area-header">工具调用</div>
      <div className="tool-list">
        {executions.map((ex) => (
          <div
            key={ex.toolCallId}
            className={`tool-item tool-item--${ex.status.toLowerCase()}`}
          >
            <span className="tool-name">{ex.toolName}</span>
            <span className={`tool-status tool-status--${ex.status.toLowerCase()}`}>
              {ex.status}
            </span>
            {ex.content && (
              <span className="tool-content">{ex.content}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
