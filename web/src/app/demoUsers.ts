// The four INTERNAL demo accounts shown on login and in the switch-user menu (SPEC §9, prototype
// USERS). Password is the shared demo password. `can`/`cannot` drive the v2 login permission card.
export interface DemoUser {
  id: string
  name: string
  email: string
  role: string
  desc: string
  can: string[]
  cannot: string[]
}

export const DEMO_USERS: DemoUser[] = [
  {
    id: 'strat',
    name: 'Aditi Rao',
    email: 'aditi.rao@parallax.dev',
    role: 'STRATEGIST',
    desc: 'Drafts and replays rule versions',
    can: ['Draft candidate versions', 'Run replays & shadow mode', 'Propose for approval'],
    cannot: ['Approve own proposals', 'Override decisions'],
  },
  {
    id: 'appr',
    name: 'Vikram Nair',
    email: 'vikram.nair@parallax.dev',
    role: 'APPROVER',
    desc: 'Approves and promotes versions',
    can: ['Approve or reject proposals', 'Promote to LIVE', 'Roll back in one step'],
    cannot: ['Draft or replay versions', 'Work the review queue'],
  },
  {
    id: 'uw',
    name: 'Priya Menon',
    email: 'priya.menon@parallax.dev',
    role: 'UNDERWRITER',
    desc: 'Works the review queue',
    can: ['Review referred applications', 'Record overrides with notes', 'Read full decisions'],
    cannot: ['Change rule versions', 'Promote or roll back'],
  },
  {
    id: 'aud',
    name: 'Sam Iyer',
    email: 'sam.iyer@parallax.dev',
    role: 'AUDITOR',
    desc: 'Reads the ledger, PII masked',
    can: ['Verify the hash chain', 'Reproduce any decision', 'Read every record'],
    cannot: ['Write to the ledger', 'See applicant names'],
  },
]

export const DEMO_PASSWORD = 'demo-password'
