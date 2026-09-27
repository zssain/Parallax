// The prototype's `I` icon paths + `ic()` helper, as a React <Icon> component (same 24×24 viewBox,
// 1.7 stroke). Two new icons in the same style for the LIFECYCLE nav: `accounts` (a card: rect +
// stripe) and `collections` (a phone receiver — outreach).
export const ICONS: Record<string, string> = {
  overview: '<path d="M3 13h4l3-8 4 14 3-6h4"/>',
  apply:
    '<path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z"/><path d="M14 3v5h5M12 11v6M9 14h6"/>',
  decisions: '<path d="M4 6h16M4 12h16M4 18h10"/>',
  queue: '<path d="M12 3l9 4-9 4-9-4z"/><path d="M3 12l9 4 9-4M3 17l9 4 9-4"/>',
  lab: '<path d="M9 3h6M10 3v6L4 19a1 1 0 0 0 1 2h14a1 1 0 0 0 1-2l-6-10V3"/><path d="M7 14h10"/>',
  drift: '<path d="M3 17l5-5 4 4 8-8"/><path d="M15 8h5v5"/>',
  ledger: '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 7h8M8 11h8M8 15h5"/>',
  assistant:
    '<path d="M12 3l1.8 4.6L18 9l-4.2 1.6L12 15l-1.8-4.4L6 9l4.2-1.4z"/><path d="M19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/>',
  system:
    '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9 7 7M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"/>',
  collapse: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16M15 10l-2 2 2 2"/>',
  out: '<path d="M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.4 1.4M17.6 17.6 19 19M5 19l1.4-1.4M17.6 6.4 19 5"/>',
  mon: '<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/>',
  moon: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
  // New LIFECYCLE nav icons, same 1.7-stroke language:
  accounts: '<rect x="3" y="6" width="18" height="12" rx="2"/><path d="M3 10h18M7 14h4"/>',
  collections:
    '<path d="M5 4h3l1.6 4-2 1.4a11 11 0 0 0 5 5l1.4-2 4 1.6V17a2 2 0 0 1-2.2 2A14 14 0 0 1 3 6.2 2 2 0 0 1 5 4z"/>',
}

export function Icon({ name, size = 19 }: { name: string; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      dangerouslySetInnerHTML={{ __html: ICONS[name] || '' }}
    />
  )
}
