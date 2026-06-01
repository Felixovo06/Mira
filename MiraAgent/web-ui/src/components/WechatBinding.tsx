import { useCallback, useEffect, useRef, useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { getWeixinStatus, pollWeixin, requestWeixinQr } from '../api'
import type { WeixinLoginState } from '../types'
import './WechatBinding.css'

interface Props {
  userId: string
}

const STEPS = [
  '打开微信，点击右上角「+」选择「扫一扫」',
  '扫描左侧二维码，在手机上确认登录',
  '确认后即可在微信里与你的角色对话',
]

const STATUS_META: Record<string, { label: string; cls: string }> = {
  logged_in: { label: '已登录', cls: 'ok' },
  qr: { label: '等待扫码', cls: 'warn' },
  scaned: { label: '已扫码，待确认', cls: 'warn' },
  expired: { label: '二维码已过期', cls: 'warn' },
  error: { label: '连接异常', cls: 'err' },
  disabled: { label: '微信未启用', cls: 'err' },
  idle: { label: '准备中', cls: 'warn' },
}

export default function WechatBinding({ userId }: Props) {
  const [st, setSt] = useState<WeixinLoginState | null>(null)
  const [loading, setLoading] = useState(false)
  const timer = useRef<number | null>(null)

  const fetchQr = useCallback(async () => {
    setLoading(true)
    try {
      setSt(await requestWeixinQr())
    } catch (e) {
      setSt({ loggedIn: false, status: 'error', message: String(e) })
    } finally {
      setLoading(false)
    }
  }, [])

  // 初始：查状态；未登录且已启用则拉二维码
  useEffect(() => {
    getWeixinStatus()
      .then((s) => {
        setSt(s)
        if (!s.loggedIn && s.status !== 'disabled') void fetchQr()
      })
      .catch((e) => setSt({ loggedIn: false, status: 'error', message: String(e) }))
  }, [fetchQr])

  // 轮询扫码状态
  useEffect(() => {
    if (timer.current) {
      clearInterval(timer.current)
      timer.current = null
    }
    if (st && (st.status === 'qr' || st.status === 'scaned')) {
      timer.current = window.setInterval(async () => {
        try {
          setSt(await pollWeixin())
        } catch {
          /* 忽略瞬时轮询失败 */
        }
      }, 2500)
    }
    return () => {
      if (timer.current) clearInterval(timer.current)
    }
  }, [st?.status])

  const meta = STATUS_META[st?.status ?? 'idle'] ?? STATUS_META.idle
  const showQr = st && !st.loggedIn && st.status !== 'disabled'

  return (
    <div className="wx glass">
      <header className="wx-head">
        <div>
          <h1 className="wx-title">微信登录</h1>
          <p className="wx-sub">扫码登录 bot 后，微信消息将进入同一个 Agent Runtime</p>
        </div>
        <span className="pill wx-status">
          <span className={`dot ${meta.cls}`} />
          {meta.label}
        </span>
      </header>

      <div className="wx-body">
        <div className="wx-qr-wrap">
          <div className="wx-qr-card">
            <div className="wx-qr-frame">
              {st?.loggedIn ? (
                <div className="wx-done">
                  <svg viewBox="0 0 24 24" width="56" height="56" fill="none" stroke="var(--accent)" strokeWidth="2">
                    <path d="M20 6 9 17l-5-5" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                  <span>已登录</span>
                </div>
              ) : showQr && st?.qrImage ? (
                <div className="wx-qr-img">
                  <QRCodeSVG value={st.qrImage} size={196} level="M" bgColor="#ffffff" fgColor="#0a1410" />
                </div>
              ) : (
                <div className="wx-qr-ph">
                  {st?.status === 'disabled' ? '微信未启用' : loading ? '获取二维码中…' : '无二维码'}
                </div>
              )}
              <span className="wx-corner tl" />
              <span className="wx-corner tr" />
              <span className="wx-corner bl" />
              <span className="wx-corner br" />
            </div>
            {showQr && (
              <button className="btn btn-ghost wx-refresh" onClick={() => void fetchQr()} disabled={loading}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="14" height="14">
                  <path d="M21 12a9 9 0 1 1-2.6-6.4M21 4v5h-5" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                刷新二维码
              </button>
            )}
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
            <div className="wx-bound-label">Web 端用户</div>
            <div className="wx-bound-id mono">{userId}</div>
          </div>

          {st?.message && (
            <div className="wx-note">
              <span className={`dot ${meta.cls}`} />
              {st.message}
            </div>
          )}
          {st?.status === 'disabled' && (
            <div className="wx-note">
              <span className="dot err" />
              在 application-local.yaml 配置 <code>mira.weixin.enabled=true</code> 后重启即可启用扫码登录。
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
