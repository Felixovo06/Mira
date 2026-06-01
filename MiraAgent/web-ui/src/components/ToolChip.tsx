import { useState } from 'react'
import './ToolChip.css'

interface Props {
  name: string
  content: string | null
}

export default function ToolChip({ name, content }: Props) {
  const [open, setOpen] = useState(false)
  const pending = content == null
  return (
    <div className={`tool-chip ${pending ? 'pending' : ''}`}>
      <button className="tool-chip-head" onClick={() => content && setOpen((v) => !v)}>
        <span className="tool-chip-ic">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" width="14" height="14">
            <path d="M14.7 6.3a4 4 0 0 1-5.4 5.4L4 17v3h3l5.3-5.3a4 4 0 0 1 5.4-5.4l-2.7 2.7-1.4-1.4 2.1-2.9Z" strokeLinejoin="round" />
          </svg>
        </span>
        <span className="tool-chip-name mono">{name}</span>
        <span className="tool-chip-status">{pending ? '执行中…' : '完成'}</span>
        {content && (
          <svg className={`tool-chip-caret ${open ? 'open' : ''}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13">
            <path d="M6 9l6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        )}
      </button>
      {open && content && <pre className="tool-chip-body mono">{content}</pre>}
    </div>
  )
}
