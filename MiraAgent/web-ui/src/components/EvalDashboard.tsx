import { useEffect, useRef, useState } from 'react'
import { getEvalReport, runEval } from '../api'
import type { EvalReport } from '../types'
import './EvalDashboard.css'

const LABELS: Record<string, string> = {
  layer1_unit: 'L1 单元级',
  layer2_chain: 'L2 链路级',
  layer3_quality: 'L3 质量级 (Judge)',
  self_improvement: '自我改善',
  tool_selection_accuracy: '工具选择准确率',
  parameter_accuracy: '参数准确率',
  no_tool_rate: 'no-tool 率',
  tool_execution_success_rate: '工具执行成功率',
  mcp_execution_success_rate: 'MCP 执行成功率',
  e2e_success_rate: 'E2E 成功率',
  tool_loop_success_rate: '工具链完成率',
  ttft_ms_avg: 'TTFT 均值',
  ttft_ms_p95: 'TTFT p95',
  latency_ms_avg: '延迟均值',
  latency_ms_p95: '延迟 p95',
  avg_tokens_per_turn: 'token/轮',
  relevance_avg: '相关性',
  persona_consistency_avg: '角色一致',
  tool_usage_avg: '工具使用',
  overall_avg: '综合',
  judged_cases: '评分用例数',
  review_trigger_accuracy: '复盘触发准确率',
  fact_assertion_pass_rate: '事实断言通过率',
  total_cases: '用例总数',
}

const RATE_KEYS = /accuracy|rate|consistency|relevance|usage|overall/i
const MS_KEYS = /_ms_/

function label(k: string) {
  return LABELS[k] ?? k
}

function fmt(key: string, v: unknown): string {
  if (v === null || v === undefined) return '—'
  if (typeof v === 'number') {
    if (MS_KEYS.test(key)) return v >= 1000 ? `${(v / 1000).toFixed(1)}s` : `${v}ms`
    if (RATE_KEYS.test(key) && v <= 1) {
      // 1-5 的 Judge 分数 vs 0-1 的比率：>1 当分数，否则百分比
      return key.endsWith('_avg') && !key.startsWith('avg') ? v.toFixed(2) : `${(v * 100).toFixed(0)}%`
    }
    return String(v)
  }
  return String(v)
}

function tone(key: string, v: unknown): string {
  if (typeof v !== 'number') return ''
  if (RATE_KEYS.test(key) && v <= 1) return v >= 0.9 ? 'good' : v >= 0.7 ? 'mid' : 'bad'
  if (key.endsWith('_avg') && v > 1 && v <= 5) return v >= 4 ? 'good' : v >= 3 ? 'mid' : 'bad'
  return ''
}

export default function EvalDashboard() {
  const [report, setReport] = useState<EvalReport | null>(null)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const poll = useRef<number | null>(null)

  async function refresh() {
    try {
      const env = await getEvalReport()
      setRunning(env.running)
      if (env.report) setReport(env.report)
      if (env.error) setError(env.error)
      if (!env.running && poll.current) {
        clearInterval(poll.current)
        poll.current = null
      }
    } catch (e) {
      setError(String(e))
    }
  }

  useEffect(() => {
    void refresh()
    return () => {
      if (poll.current) clearInterval(poll.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function start() {
    setError(null)
    try {
      await runEval()
      setRunning(true)
      if (!poll.current) poll.current = window.setInterval(() => void refresh(), 2500)
    } catch (e) {
      setError(String(e))
    }
  }

  const summary = report?.summary ?? {}
  const groups = Object.entries(summary).filter(([, v]) => typeof v === 'object' && v !== null)
  const scalars = Object.entries(summary).filter(([, v]) => typeof v !== 'object' || v === null)

  return (
    <div className="evald glass">
      <header className="evald-head">
        <div>
          <h1 className="evald-title">评测 Dashboard</h1>
          <p className="evald-sub">
            {running ? '评测进行中…（黑盒跑真实 Agent，约 1-2 分钟）' : '四层评测 · 黑盒跑运行中的 Agent'}
          </p>
        </div>
        <button className="btn btn-primary" onClick={() => void start()} disabled={running}>
          {running ? <span className="evald-spin" /> : '▶ 运行评测'}
        </button>
      </header>

      {error && <div className="evald-error">{error}</div>}

      <div className="evald-scroll">
        {!report && !running && (
          <div className="evald-empty">
            <div className="empty-ring" />
            <p>还没有评测报告</p>
            <span>点右上角「运行评测」，对当前运行的 Agent 跑一遍用例集。</span>
          </div>
        )}

        {/* 分层指标卡 */}
        {groups.map(([gk, gv]) => (
          <section key={gk} className="evald-group">
            <h2 className="evald-gtitle">{label(gk)}</h2>
            <div className="evald-cards">
              {Object.entries(gv as Record<string, unknown>).map(([k, v]) => (
                <div key={k} className={`evald-card ${tone(k, v)}`}>
                  <span className="evald-cval">{fmt(k, v)}</span>
                  <span className="evald-clabel">{label(k)}</span>
                </div>
              ))}
            </div>
          </section>
        ))}

        {/* 标量指标 */}
        {scalars.length > 0 && (
          <section className="evald-group">
            <h2 className="evald-gtitle">总体</h2>
            <div className="evald-cards">
              {scalars.map(([k, v]) => (
                <div key={k} className={`evald-card ${tone(k, v)}`}>
                  <span className="evald-cval">{fmt(k, v)}</span>
                  <span className="evald-clabel">{label(k)}</span>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* 回归对比 */}
        {report?.diff && (
          <section className="evald-group">
            <h2 className="evald-gtitle">
              vs baseline · {report.diff.regression_count} 回归 / {report.diff.improvement_count} 改进
            </h2>
            <div className="evald-difflist">
              {([
                ...report.diff.regressions.map((r) => ({ row: r, kind: 'reg' as const })),
                ...report.diff.improvements.map((r) => ({ row: r, kind: 'imp' as const })),
              ]).map(({ row, kind }, i) => (
                <div key={i} className={`evald-diff ${kind === 'reg' ? 'bad' : 'good'}`}>
                  <span className="mono">{String(row.metric)}</span>
                  <span>
                    {String(row.baseline)} → {String(row.current)} (
                    {Number(row.delta) > 0 ? '+' : ''}{String(row.delta)})
                  </span>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* 用例明细 */}
        {report?.cases && report.cases.length > 0 && (
          <section className="evald-group">
            <h2 className="evald-gtitle">用例明细 ({report.cases.length})</h2>
            <table className="evald-table">
              <thead>
                <tr><th>用例</th><th>分类</th><th>结果</th><th>工具</th><th>TTFT</th><th>token</th></tr>
              </thead>
              <tbody>
                {report.cases.map((c, i) => (
                  <tr key={i}>
                    <td className="mono">{String(c.id ?? '')}</td>
                    <td>{String(c.category ?? '')}</td>
                    <td className={c.ok ? 'ok' : 'fail'}>{c.ok ? 'OK' : 'FAIL'}</td>
                    <td className="mono evald-tools">{String(c.calledTools ?? '')}</td>
                    <td>{c.ttftMs != null ? `${(Number(c.ttftMs) / 1000).toFixed(1)}s` : '—'}</td>
                    <td>{String(c.tokens ?? '—')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        )}
      </div>
    </div>
  )
}
