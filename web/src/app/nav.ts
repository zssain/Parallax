// The prototype's NAV / IDX / TITLE, as shared shell metadata. The nine core pages match the
// standalone v2 prototype exactly; a fourth dock group carries the two LIFECYCLE screens (Accounts,
// Collections) that the running app adds beyond the prototype (see docs/DECISIONS.md).
export interface NavMeta {
  key: string
  label: string // short dock label
  title: string // full crumb / page title
  idx: string
  path: string
}

export const NAV_GROUPS: NavMeta[][] = [
  [
    { key: 'overview', label: 'Overview', title: 'Overview', idx: '01', path: '/app' },
    { key: 'apply', label: 'Apply', title: 'New application', idx: '02', path: '/app/apply' },
    { key: 'decisions', label: 'Decisions', title: 'Decisions', idx: '03', path: '/app/decisions' },
    { key: 'queue', label: 'Queue', title: 'Review queue', idx: '04', path: '/app/queue' },
  ],
  [
    { key: 'lab', label: 'Lab', title: 'Strategy Lab', idx: '05', path: '/app/lab' },
    { key: 'drift', label: 'Drift', title: 'Drift monitor', idx: '06', path: '/app/drift' },
  ],
  [
    { key: 'ledger', label: 'Ledger', title: 'Ledger', idx: '07', path: '/app/ledger' },
    { key: 'assistant', label: 'Assistant', title: 'Assistant', idx: '08', path: '/app/assistant' },
    { key: 'system', label: 'System', title: 'System', idx: '09', path: '/app/system' },
  ],
  [
    { key: 'accounts', label: 'Accounts', title: 'Accounts', idx: '10', path: '/app/accounts' },
    { key: 'collections', label: 'Collections', title: 'Collections', idx: '11', path: '/app/collections' },
  ],
]

export const NAV_ALL: NavMeta[] = NAV_GROUPS.flat()

/** Resolve the current pathname to its nav metadata plus any detail suffix for the crumb. */
export function activeMeta(pathname: string): { meta: NavMeta; detail?: string } {
  // Detail routes borrow their parent's number and highlight the parent dock item.
  const decisionDetail = pathname.match(/^\/app\/decisions\/(.+)$/)
  if (decisionDetail) {
    return { meta: { ...NAV_ALL.find((n) => n.key === 'decisions')!, title: 'Decision' }, detail: decisionDetail[1] }
  }
  const accountDetail = pathname.match(/^\/app\/accounts\/(.+)$/)
  if (accountDetail) {
    return { meta: { ...NAV_ALL.find((n) => n.key === 'accounts')!, title: 'Account' }, detail: accountDetail[1] }
  }
  const exact =
    NAV_ALL.find((n) => n.path === pathname) ||
    NAV_ALL.find((n) => n.path !== '/app' && pathname.startsWith(n.path + '/')) ||
    NAV_ALL[0]
  return { meta: exact }
}

export function isNavActive(pathname: string, item: NavMeta): boolean {
  if (item.path === '/app') return pathname === '/app' || pathname === '/app/'
  return pathname === item.path || pathname.startsWith(item.path + '/')
}
