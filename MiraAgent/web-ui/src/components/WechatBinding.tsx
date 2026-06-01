import { useState } from 'react'
import FauxQR from './FauxQR'
import './WechatBinding.css'

interface Props {
  userId: string
}

const STEPS = [
  '打开微信，点击右上角「+」选择「扫一扫」',
  '扫描左侧二维码，进入绑定确认页',
  '确认后即可在微信中与你的 Mira 对话',
]

export default function WechatBinding({ userId }: Props) {
  // 后端尚无绑定接口，二维码内容为占位 token；刷新仅重新生成占位图。
  const [nonce, setNonce] = useState(0)
  const qrValue = `miraagent://bind?user=${userId}&n=${nonce}`

  return (
    <div className="wx glass">
      <header className="wx-head">
        <div>
          <h1 className="wx-title">微信绑定</h1>
          <p className="wx-sub">扫码后，即可在微信里随时找到这个角色</p>
        </div>
        <span className="pill wx-status">
          <span className="dot warn" />
          等待扫码
        </span>
      </header>

      <div className="wx-body">
        <div className="wx-qr-wrap">
          <div className="wx-qr-card">
            <div className="wx-qr-frame">
              <FauxQR value={qrValue} size={196} />
              <span className="wx-corner tl" />
              <span className="wx-corner tr" />
              <span className="wx-corner bl" />
              <span className="wx-corner br" />
              <div className="wx-logo">
                <svg viewBox="0 0 24 24" width="22" height="22" fill="#fff">
                  <path d="M9 4C5.1 4 2 6.6 2 9.9c0 1.8 1 3.4 2.6 4.5L4 16.6l2.3-1.2c.8.2 1.6.3 2.4.3h.5a4.6 4.6 0 0 1-.2-1.4c0-3 2.9-5.2 6.3-5.2h.6C15.3 6 12.4 4 9 4Zm-2.4 4.2a.9.9 0 1 1 0 1.8.9.9 0 0 1 0-1.8Zm4.8 0a.9.9 0 1 1 0 1.8.9.9 0 0 1 0-1.8Z" />
                  <path d="M22 14.3c0-2.7-2.6-4.9-5.8-4.9s-5.8 2.2-5.8 4.9 2.6 4.9 5.8 4.9c.7 0 1.3-.1 1.9-.3l1.9 1-.5-1.7c1.5-.9 2.5-2.3 2.5-3.9Zm-7.7-.8a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5Zm3.8 0a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5Z" />
                </svg>
              </div>
            </div>
            <button className="btn btn-ghost wx-refresh" onClick={() => setNonce((n) => n + 1)}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="14" height="14">
                <path d="M21 12a9 9 0 1 1-2.6-6.4M21 4v5h-5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              刷新二维码
            </button>
          </div>
        </div>

        <div className="wx-info">
          <ol className="wx-steps">
            {STEPS.map((s, i) => (
              <li key={i} style={{ animationDelay: `${i * 0.08}s` }}>
                <span className="wx-step-n">{i + 1}</span>
                <span>{s}</span>
              </li>
            ))}
          </ol>

          <div className="wx-bound">
            <div className="wx-bound-label">当前绑定用户</div>
            <div className="wx-bound-id mono">{userId}</div>
          </div>

          <div className="wx-note">
            <span className="dot warn" />
            后端微信绑定接口尚未接入，此二维码为占位图；接口就绪后将显示可扫码绑定的真实二维码与实时绑定状态。
          </div>
        </div>
      </div>
    </div>
  )
}
