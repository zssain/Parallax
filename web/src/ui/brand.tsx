// The Parallax brand mark, inline as SVG so it scales crisply and themes cleanly: two vertical bars
// (rust + gold — the "ll" of the wordmark) with a gold dot at the foot. Replaces the old two-circle
// mark. The <Wordmark> pairs it with the italic-serif "parallax" text used across the shell,
// marketing and login (the prototype's `.wm`).
export function Mark({ size = 22 }: { size?: number }) {
  const h = size
  const w = Math.round((30 / 34) * size)
  return (
    <span className="mk" aria-hidden="true">
      <svg width={w} height={h} viewBox="0 0 30 34" fill="none">
        <rect x="7" y="7" width="5" height="25" rx="1" fill="#b0492f" />
        <rect x="15" y="2" width="5" height="25" rx="1" fill="#c9a45a" />
        <circle cx="25.5" cy="29" r="3.6" fill="#c9a45a" />
      </svg>
    </span>
  )
}

export function Wordmark({
  className = '',
  markSize = 22,
  onClick,
}: {
  className?: string
  markSize?: number
  onClick?: () => void
}) {
  return (
    <div className={`wm ${className}`} onClick={onClick}>
      <Mark size={markSize} />
      <span>parallax</span>
    </div>
  )
}
