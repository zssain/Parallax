// Escape HTML, then re-permit only <b>…</b>. Toast and assistant text are app-controlled, but this
// keeps any interpolated value (applicant text, API detail) from injecting markup.
export function sanitizeInline(s: string): string {
  const esc = String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return esc.replace(/&lt;b&gt;/g, '<b>').replace(/&lt;\/b&gt;/g, '</b>')
}
