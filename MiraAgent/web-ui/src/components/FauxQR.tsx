// 占位二维码：后端尚无微信绑定/二维码接口，这里按 value 确定性地生成一个「像二维码」的图形，
// 待后端返回真实绑定 token 后，把 value 换成真实内容（或改用真实 QR 库）即可。

interface Props {
  value: string
  size?: number
  fg?: string
}

export default function FauxQR({ value, size = 196, fg = '#0a1410' }: Props) {
  const n = 25
  const quiet = 2
  const total = n + quiet * 2
  const cell = size / total

  // FNV-1a 哈希 + xorshift，保证同 value 稳定输出
  let h = 2166136261
  for (let i = 0; i < value.length; i++) {
    h ^= value.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  const rand = () => {
    h ^= h << 13
    h ^= h >>> 17
    h ^= h << 5
    return ((h >>> 0) % 1000) / 1000
  }

  const inFinder = (r: number, c: number) => {
    const box = (br: number, bc: number) => r >= br && r < br + 7 && c >= bc && c < bc + 7
    return box(0, 0) || box(0, n - 7) || box(n - 7, 0)
  }

  const modules: { x: number; y: number }[] = []
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      if (inFinder(r, c)) continue
      if (rand() > 0.52) modules.push({ x: (c + quiet) * cell, y: (r + quiet) * cell })
    }
  }

  const finders = [
    { r: 0, c: 0 },
    { r: 0, c: n - 7 },
    { r: n - 7, c: 0 },
  ]

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} shapeRendering="crispEdges">
      {modules.map((m, i) => (
        <rect key={i} x={m.x} y={m.y} width={cell * 0.92} height={cell * 0.92} rx={cell * 0.22} fill={fg} />
      ))}
      {finders.map((f, i) => {
        const x = (f.c + quiet) * cell
        const y = (f.r + quiet) * cell
        return (
          <g key={`f${i}`}>
            <rect x={x} y={y} width={cell * 7} height={cell * 7} rx={cell * 1.6} fill="none" stroke={fg} strokeWidth={cell} />
            <rect x={x + cell * 2} y={y + cell * 2} width={cell * 3} height={cell * 3} rx={cell * 0.8} fill={fg} />
          </g>
        )
      })}
    </svg>
  )
}
