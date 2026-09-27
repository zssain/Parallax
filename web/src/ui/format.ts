// Formatting helpers identical to the prototype's (money, pct, fmt), plus a cents variant for the
// account-service money and an ISO → "YYYY-MM-DD HH:mm" timestamp.
export const fmt = (n: number) => Math.round(n).toLocaleString('en-US')
export const money = (n: number) => (n < 0 ? '−$' : '$') + Math.abs(Math.round(n)).toLocaleString('en-US')
export const moneyCents = (cents: number) => money(cents / 100)
export const pct = (v: number, d = 0) => (v * 100).toFixed(d) + '%'
export const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v))
export const cap = (s: string) => (s ? s[0] + s.slice(1).toLowerCase() : s)

/** ISO-8601 instant → "2026-09-25 09:14" (matches the prototype's ledger/decisions timestamps). */
export function fmtTs(iso?: string | null): string {
  if (!iso) return '—'
  return iso.length >= 16 ? iso.slice(0, 10) + ' ' + iso.slice(11, 16) : iso
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
/** "2025-10" → "Oct 2025". */
export function fmtMonth(ym: string): string {
  const [y, m] = ym.split('-')
  return `${MONTHS[Number(m) - 1]} ${y}`
}

/** PSI status ("stable"/"watch"/"investigate") → text-colour class. */
export function psiCls(status?: string): string {
  return status === 'stable' ? 't-ok' : status === 'watch' ? 't-warn' : status === 'investigate' ? 't-bad' : ''
}

