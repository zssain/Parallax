// The marketing hero is a live, client-side replay: 20,000 synthetic applicants scored once, then
// re-approved as the visitor drags the cutoff. Ported verbatim from the standalone v2 prototype's
// decision engine + synthetic-history generator so the landing page needs no backend. All data here
// is synthetic and never leaves the browser.
import { clamp } from '../ui/format'

interface Applicant {
  age: number
  income: number
  housing: number
  debt: number
  util: number
  inq: number
  delq: number
  trades: number
  fileAge: number
  indepIncome: boolean
  consent: boolean
  addrMismatch: boolean
  birthYear: number
  ssnYear: number
  deceased: boolean
  velocity: number
}
interface Cfg {
  approveCutoff: number
  referCutoff: number
  minPayPct: number
  atpShare: number
  livingCost: number
  minLimit: number
  bandLimits: number[][]
  utilPts: number[]
  inqPts: number[]
}

const BASE_CFG: Cfg = {
  approveCutoff: 680,
  referCutoff: 620,
  minPayPct: 0.03,
  atpShare: 0.35,
  livingCost: 1200,
  minLimit: 300,
  bandLimits: [
    [800, 12000],
    [760, 7500],
    [720, 4000],
    [680, 2000],
    [0, 1000],
  ],
  utilPts: [130, 115, 85, 45, 10],
  inqPts: [90, 70, 35, 5],
}

// Mulberry32 PRNG + FNV-1a hash, matching the prototype exactly (deterministic history + ledger art).
function rng(a: number) {
  return function () {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
function h32(s: string) {
  let h = 0x811c9dc5
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return (h >>> 0).toString(16).padStart(8, '0')
}
const hash = (s: string) => h32(s) + h32('#' + s)
const GENESIS = '0000000000000000'

function scorePoints(x: Applicant, c: Cfg): number {
  const u = x.util
  const ui = u < 0.1 ? 0 : u < 0.3 ? 1 : u < 0.5 ? 2 : u < 0.75 ? 3 : 4
  const ii = x.inq === 0 ? 0 : x.inq <= 2 ? 1 : x.inq <= 4 ? 2 : 3
  const di = x.delq === 0 ? 0 : x.delq === 1 ? 1 : 2
  const ti = x.trades <= 1 ? 0 : x.trades <= 4 ? 1 : x.trades <= 10 ? 2 : 3
  const fi = x.fileAge < 24 ? 0 : x.fileAge < 60 ? 1 : x.fileAge < 120 ? 2 : 3
  const ni = x.income < 25000 ? 0 : x.income < 50000 ? 1 : x.income < 100000 ? 2 : 3
  return (
    c.utilPts[ui] +
    c.inqPts[ii] +
    [140, 60, 10][di] +
    [20, 55, 70, 60][ti] +
    [15, 40, 60, 70][fi] +
    [10, 25, 40, 50][ni]
  )
}

interface Evaluated {
  outcome: 'APPROVED' | 'REFER' | 'DECLINED'
  score: number
  eligible: boolean
}
function evaluate(x: Applicant, c: Cfg): Evaluated {
  const fraud = x.addrMismatch || x.ssnYear < x.birthYear || x.deceased || x.velocity >= 3
  const residual = x.income / 12 - x.housing - x.debt - c.livingCost
  const atpMax = Math.max(0, Math.floor((residual * c.atpShare) / c.minPayPct / 100) * 100)
  const checksPass = x.age >= 18 && (x.age >= 21 || x.indepIncome) && x.consent && atpMax >= c.minLimit
  const score = 300 + scorePoints(x, c)
  let outcome: Evaluated['outcome']
  if (fraud) outcome = 'REFER'
  else if (!checksPass) outcome = 'DECLINED'
  else outcome = score >= c.approveCutoff ? 'APPROVED' : score >= c.referCutoff ? 'REFER' : 'DECLINED'
  return { outcome, score, eligible: !fraud && checksPass }
}

function genApplicant(r: () => number, drift = 0): Applicant {
  const t = r()
  const tier = t < 0.32 ? 0 : t < 0.78 ? 1 : 2
  const util = clamp(r() * [0.3, 0.62, 0.95][tier] + drift * 0.2 * r(), 0, 0.99)
  const inq = Math.min(8, Math.floor(-Math.log(1 - r() * 0.999) * [0.6, 1.5, 2.8][tier] + drift * r() * 1.5))
  const delq = r() < [0.03, 0.14, 0.42][tier] ? (r() < 0.6 ? 1 : 2 + Math.floor(r() * 2)) : 0
  const trades = 1 + Math.floor(r() * [18, 11, 7][tier])
  const fileAge = 6 + Math.floor(r() * [260, 150, 90][tier])
  const income = Math.round((24000 + r() * r() * 150000 + [30000, 8000, 0][tier]) / 1000) * 1000
  const housing = Math.round((500 + r() * 1700) / 50) * 50
  const debt = Math.round((r() * [500, 700, 1100][tier]) / 10) * 10
  const age = r() < 0.04 ? 18 + Math.floor(r() * 3) : 21 + Math.floor(r() * 50)
  const birthYear = 2026 - age
  return {
    age,
    income,
    housing,
    debt,
    util: +util.toFixed(3),
    inq,
    delq,
    trades,
    fileAge,
    indepIncome: r() < 0.55,
    consent: true,
    addrMismatch: r() < 0.008,
    birthYear,
    ssnYear: birthYear + (r() < 0.002 ? -2 : 1 + Math.floor(r() * 15)),
    deceased: r() < 0.001,
    velocity: r() < 0.004 ? 3 : 1,
  }
}

interface HeroRow {
  s: number
  el: boolean
  obs: boolean
}
const HERO: HeroRow[] = (() => {
  const r = rng(20260925)
  const rows: HeroRow[] = []
  for (let i = 0; i < 20000; i++) {
    const x = genApplicant(r)
    const e = evaluate(x, BASE_CFG)
    rows.push({ s: e.score, el: e.eligible, obs: e.outcome === 'APPROVED' })
  }
  return rows
})()

export const HERO_LEN = HERO.length

/** 40 histogram bins (scores 450–850, 10 pts each). */
export const HB: number[] = (() => {
  const b = Array(40).fill(0)
  HERO.forEach((h) => {
    b[clamp(Math.floor((h.s - 450) / 10), 0, 39)]++
  })
  return b
})()

export interface HeroCalc {
  la: number
  ca: number
  fl: number
  unk: number
}
/** Approval rate live→candidate, flips and outcome-unknown for a candidate approve cutoff. */
export function heroCalc(c: number): HeroCalc {
  let la = 0,
    ca = 0,
    fl = 0,
    unk = 0
  for (const h of HERO) {
    const a = h.el && h.s >= c
    if (h.obs) la++
    if (a) ca++
    if (a !== h.obs) fl++
    if (a && !h.obs) unk++
  }
  return { la: la / HERO.length, ca: ca / HERO.length, fl, unk }
}

/** Three decorative ledger links for the "Record" art panel (a real FNV hash chain). */
export const ART_LEDGER = (() => {
  const rows: { seq: number; prev: string; hash: string }[] = []
  let prev = GENESIS
  for (let i = 0; i < 15; i++) {
    const h = hash(`seed-${i}|${prev}`)
    rows.push({ seq: i + 1, prev, hash: h })
    prev = h
  }
  return rows.slice(-3)
})()

/** The five-line audit questions. */
export interface Quiz {
  q: string
  d: string
  o: [string, number][]
  f: string
}
export const QZ: Quiz[] = [
  {
    q: 'Can you reproduce a decision from six months ago — exactly?',
    d: 'Rebuilding an answer from logs is not the same as re-running the same inputs under the same rules.',
    o: [
      ['Yes, byte for byte', 2],
      ['Roughly, from logs', 1],
      ['Not really', 0],
    ],
    f: 'Decision ledger + reproduce',
  },
  {
    q: 'Before a rule change ships, do you know which past applicants it would flip?',
    d: 'Move a cutoff twenty points and one band can change sharply while another barely moves.',
    o: [
      ['Yes, replayed on history', 2],
      ['We estimate in a spreadsheet', 1],
      ['We find out after launch', 0],
    ],
    f: 'Strategy Lab replay',
  },
  {
    q: 'Does your loss estimate separate observed from guessed?',
    d: 'Loosening a rule approves people nobody has ever observed.',
    o: [
      ['Yes, explicitly', 2],
      ['Sometimes', 1],
      ['We treat them the same', 0],
    ],
    f: 'Reject-inference labelling',
  },
  {
    q: 'Can the person who proposes a rule change also approve it?',
    d: 'Good governance needs two sets of eyes — and a rollback that takes minutes.',
    o: [
      ['No, maker-checker enforced', 2],
      ['By convention only', 1],
      ['Yes', 0],
    ],
    f: 'Maker-checker + rollback',
  },
  {
    q: 'What happens to applications when your credit bureau goes down?',
    d: 'An outage should degrade into a review queue, not an error page.',
    o: [
      ['They queue and re-decide', 2],
      ['A manual workaround', 1],
      ['They fail', 0],
    ],
    f: 'Circuit breaker + re-decision',
  },
]
