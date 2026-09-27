// The prototype's archArt(uid) SVG generator, ported to JSX. Gradient ids are namespaced by `uid`
// so several instances can coexist. The .plx groups keep their data-d depths so the hero/login
// mouse-move parallax can translate them.
const arch = (cx: number, hw: number, top: number, bot: number) =>
  `M ${cx - hw} ${bot} V ${top + hw} A ${hw} ${hw} 0 0 1 ${cx + hw} ${top + hw} V ${bot}`

export function ArchArt({ uid }: { uid: string }) {
  const arcsT = Array.from({ length: 15 }, (_, i) => ({
    d: arch(290, 64 + i * 12, 235 - i * 10, 540),
    opacity: (1 - i / 17).toFixed(2),
  }))
  const arcsD = Array.from({ length: 9 }, (_, i) => ({
    d: arch(350, 52 + i * 10, 262 - i * 9, 540),
    opacity: (0.75 - i / 14).toFixed(2),
  }))
  const lines = Array.from({ length: 9 }, (_, i) => ({
    x1: 40 + i * 12,
    y1: 556 + i * 14,
    x2: 600 - i * 12,
    y2: 556 + i * 14,
    op: 0.12 - i * 0.01,
  }))
  const centre = arch(320, 40, 330, 540)

  return (
    <svg className="arch-svg" viewBox="0 0 640 680" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <radialGradient id={`g${uid}`} cx="50%" cy="66%" r="42%">
          <stop offset="0" stopColor="#f6e4b4" stopOpacity=".95" />
          <stop offset=".3" stopColor="#c9b17c" stopOpacity=".55" />
          <stop offset="1" stopColor="#0b1830" stopOpacity="0" />
        </radialGradient>
        <linearGradient id={`t${uid}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#a8dcdf" stopOpacity=".85" />
          <stop offset="1" stopColor="#2e7a80" stopOpacity=".1" />
        </linearGradient>
        <linearGradient id={`d${uid}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#f3dfae" />
          <stop offset="1" stopColor="#c9b17c" stopOpacity=".35" />
        </linearGradient>
        <linearGradient id={`w${uid}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#c9b17c" stopOpacity=".35" />
          <stop offset="1" stopColor="#0b1830" stopOpacity="0" />
        </linearGradient>
      </defs>
      <rect width="640" height="680" fill="none" />
      <ellipse cx="320" cy="470" rx="300" ry="240" fill={`url(#g${uid})`} />
      <g className="plx" data-d="6">
        <path
          d="M0 520 L90 450 L160 480 L240 420 L300 500 L360 440 L450 490 L520 430 L640 500 L640 540 L0 540Z"
          fill="#0f2442"
          opacity=".9"
        />
      </g>
      <g className="plx" data-d="22">
        {arcsT.map((a, i) => (
          <path key={i} d={a.d} fill="none" stroke={`url(#t${uid})`} strokeWidth="1.3" opacity={a.opacity} />
        ))}
      </g>
      <g className="plx" data-d="-14">
        {arcsD.map((a, i) => (
          <path key={i} d={a.d} fill="none" stroke="#e6d3a3" strokeWidth="1.1" opacity={a.opacity} />
        ))}
      </g>
      <path d={centre} fill={`url(#d${uid})`} opacity=".92" />
      <rect x="0" y="540" width="640" height="140" fill="#0b1830" />
      <path d={centre} transform="translate(0 1080) scale(1 -1)" fill={`url(#w${uid})`} opacity=".55" />
      {lines.map((l, i) => (
        <line key={i} x1={l.x1} y1={l.y1} x2={l.x2} y2={l.y2} stroke="#a8dcdf" strokeOpacity={l.op} />
      ))}
    </svg>
  )
}
