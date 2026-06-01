import './MessageBubble.css'

interface Props {
  role: 'user' | 'assistant'
  content: string
  pending?: boolean
}

export default function MessageBubble({ role, content, pending }: Props) {
  const isUser = role === 'user'
  const hasContent = content.length > 0

  return (
    <div className={`row ${isUser ? 'row-user' : 'row-bot'}`}>
      {!isUser && (
        <div className="avatar">
          <span />
        </div>
      )}
      <div className={`bubble ${isUser ? 'bubble-user' : 'bubble-bot'}`}>
        {hasContent ? (
          <p className="bubble-text">
            {content}
            {pending && <span className="cursor" />}
          </p>
        ) : pending ? (
          <span className="thinking">
            <i /><i /><i />
          </span>
        ) : (
          <p className="bubble-text" />
        )}
      </div>
    </div>
  )
}
