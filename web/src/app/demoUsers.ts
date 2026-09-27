// The four INTERNAL demo accounts shown on login and in the switch-user select (SPEC §9, prototype
// USERS). Password is the shared demo password.
export interface DemoUser {
  id: string
  name: string
  email: string
  role: string
  desc: string
}

export const DEMO_USERS: DemoUser[] = [
  { id: 'strat', name: 'Aditi Rao', email: 'aditi.rao@parallax.dev', role: 'STRATEGIST', desc: 'Drafts and replays rule versions' },
  { id: 'appr', name: 'Vikram Nair', email: 'vikram.nair@parallax.dev', role: 'APPROVER', desc: 'Approves and promotes versions' },
  { id: 'uw', name: 'Priya Menon', email: 'priya.menon@parallax.dev', role: 'UNDERWRITER', desc: 'Works the review queue' },
  { id: 'aud', name: 'Sam Iyer', email: 'sam.iyer@parallax.dev', role: 'AUDITOR', desc: 'Reads the ledger, PII masked' },
]

export const DEMO_PASSWORD = 'demo-password'
