import './MessageBubble.css'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  pending?: boolean
}

interface Props {
  message: Message
}

export function MessageBubble({ message }: Props) {
  const isUser = message.role === 'user'

  return (
    <div className={`bubble-row ${isUser ? 'bubble-row--user' : 'bubble-row--assistant'}`}>
      <div className={`bubble ${isUser ? 'bubble--user' : 'bubble--assistant'}`}>
        <span className="bubble-role">{isUser ? 'You' : 'Mira'}</span>
        {message.pending ? (
          <span className="bubble-thinking">
            <span className="dot" />
            <span className="dot" />
            <span className="dot" />
          </span>
        ) : (
          <p className="bubble-content">{message.content}</p>
        )}
      </div>
    </div>
  )
}
