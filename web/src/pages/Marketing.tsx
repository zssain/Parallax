import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Wordmark } from '../ui/brand'
import { clamp, fmt, pct } from '../ui/format'
import { HB, HERO_LEN, heroCalc, ART_LEDGER, QZ, type Quiz } from './marketingEngine'

function scrollToId(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

const TICK_WORDS = [
  'DECIDE',
  'RECORD',
  'REPLAY',
  'MAKER-CHECKER',
  'ADVERSE ACTION',
  'HASH-CHAINED LEDGER',
  'SHADOW MODE',
  'DRIFT · PSI',
  'READ-ONLY AI',
]
const xPct = (c: number) => ((c - 450) / 400) * 100

export function Marketing() {
  const navigate = useNavigate()
  const [hc, setHc] = useState(700)
  const stageRef = useRef<HTMLElement>(null)
  const histRef = useRef<SVGSVGElement>(null)
  const copyRef = useRef<HTMLDivElement>(null)
  const dragRef = useRef(false)

  function setCutFromX(clientX: number) {
    const r = stageRef.current?.getBoundingClientRect()
    if (!r) return
    setHc(clamp(Math.round((450 + ((clientX - r.left) / r.width) * 400) / 5) * 5, 620, 760))
  }

  // Dragging the handle tracks pointer moves at the window level (matches the prototype).
  useEffect(() => {
    const move = (e: PointerEvent) => {
      if (dragRef.current) setCutFromX(e.clientX)
    }
    const up = () => (dragRef.current = false)
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
    return () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', up)
    }
  }, [])

  function onStageMove(e: React.MouseEvent<HTMLElement>) {
    if (dragRef.current) return
    const r = e.currentTarget.getBoundingClientRect()
    const dx = (e.clientX - r.left) / r.width - 0.5
    const dy = (e.clientY - r.top) / r.height - 0.5
    if (histRef.current) histRef.current.style.transform = `translateY(${dy * -8}px)`
    if (copyRef.current) copyRef.current.style.transform = `translate(${dx * -10}px,${dy * -6}px)`
  }

  const L = 680
  const a = Math.min(L, hc)
  const b = Math.max(L, hc)
  const mx = Math.max(...HB)
  const r = heroCalc(hc)
  const d = (r.ca - r.la) * 100
  const art = heroCalc(700)

  return (
    <div id="site">
      <section className="stage" id="stage" ref={stageRef} style={{ ['--x' as string]: xPct(hc) + '%' }} onMouseMove={onStageMove}>
        <div className="pane" />
        <header className="mh">
          <Wordmark className="blend" markSize={22} onClick={() => scrollToId('stage')} />
          <nav className="blend">
            <a onClick={() => scrollToId('how')}>Platform</a>
            <a onClick={() => scrollToId('audit')}>Audit</a>
            <a onClick={() => scrollToId('rules')}>Fine print</a>
            <a onClick={() => scrollToId('gov')}>Governance</a>
          </nav>
          <a className="blend" style={{ fontSize: 14.5 }} onClick={() => navigate('/login')}>
            Sign in
          </a>
          <button className="pill gold" style={{ padding: '11px 18px', fontSize: 14 }} onClick={() => navigate('/login')}>
            Open workspace →
          </button>
        </header>
        <div className="st-copy" id="stCopy" ref={copyRef}>
          <div className="kick">Credit decisioning · replay-first</div>
          <h1>
            Move the line.
            <br />
            <em>See who moves with it.</em>
          </h1>
          <p>
            Parallax decides credit card applications, records every decision with its exact inputs, and replays your
            full history through a rule change before a single customer sees it.
          </p>
          <div className="st-cta">
            <button className="pill" onClick={() => navigate('/login')}>
              Open the workspace →
            </button>
            <button className="tlink" onClick={() => scrollToId('how')}>
              How it works ↓
            </button>
          </div>
        </div>
        <div className="st-read" id="stRead">
          <div className="kick">Replaying {fmt(HERO_LEN)} applicants</div>
          <div className="rrow">
            <span>Approval rate</span>
            <b>
              {pct(r.la, 1)} → {pct(r.ca, 1)}
            </b>
            <em className={d < 0 ? 'dn' : d > 0 ? 'up' : ''}>
              {d >= 0 ? '+' : ''}
              {d.toFixed(1)} pts vs live
            </em>
          </div>
          <div className="rrow">
            <span>Decisions flipped</span>
            <b>{fmt(r.fl)}</b>
            <em>{pct(r.fl / HERO_LEN, 1)} of history</em>
          </div>
          <div className="rrow">
            <span>Outcome unknown</span>
            <b>{fmt(r.unk)}</b>
            <em>{r.unk ? 'never observed — not counted as safe' : 'tightening only · all observed'}</em>
          </div>
          <p>
            <i />
            Rust bars flip outcome. Drag the gold line, or click the chart.
          </p>
        </div>
        <svg
          className="hist"
          id="hist"
          ref={histRef}
          viewBox="0 0 1000 300"
          preserveAspectRatio="none"
          onClick={(e) => setCutFromX(e.clientX)}
        >
          {HB.map((n, i) => {
            const s0 = 450 + i * 10
            const h = (n / mx) * 270
            const flip = s0 >= a && s0 < b
            const color = flip ? '#b0492f' : s0 >= hc ? '#c9a45a' : 'rgba(27,24,20,.16)'
            return <rect key={i} x={i * 25 + 2} y={300 - h} width={21} height={h} fill={color} />
          })}
        </svg>
        <div className="livecut" id="liveCut" style={{ left: xPct(L) + '%' }}>
          <span>LIVE · 680</span>
        </div>
        <div className="divider">
          <div
            className="handle"
            id="handle"
            tabIndex={0}
            role="slider"
            aria-label="Candidate approve cutoff"
            aria-valuemin={620}
            aria-valuemax={760}
            aria-valuenow={hc}
            onPointerDown={(e) => {
              dragRef.current = true
              e.preventDefault()
            }}
            onKeyDown={(e) => {
              if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
                e.preventDefault()
                setHc((v) => clamp(v + (e.key === 'ArrowLeft' ? -5 : 5), 620, 760))
              }
            }}
          >
            ⇆
          </div>
          <span className="dl">
            CANDIDATE · <b id="cutv">{hc}</b>
          </span>
        </div>
      </section>

      <div className="tick">
        <div className="tick-in" id="tick">
          {[0, 1].map((dup) =>
            TICK_WORDS.map((w, i) => (
              <span key={`${dup}-${i}`} style={{ display: 'contents' }}>
                <span>{w}</span>
                <span className="d">◆</span>
              </span>
            )),
          )}
        </div>
      </div>

      <section className="sec" id="how">
        <div className="sec-l">
          <div className="kick">How it works</div>
          <h2>
            Decide.
            <br />
            Record.
            <br />
            <em>Replay.</em>
          </h2>
          <p>One deterministic engine, one tamper-evident record, and the ability to test tomorrow's rules on yesterday's applicants.</p>
        </div>
        <div>
          <div className="chap">
            <div className="cn">01</div>
            <div>
              <h3>Decide</h3>
              <p>Identity and fraud screening, legal-capacity and ability-to-pay policy, then a points-based scorecard. Approve, refer or decline — with a limit and ranked reason codes.</p>
            </div>
            <pre className="art">
              <span className="k">{'{'}</span> "outcome": <span className="g">"REFER"</span>,{'\n'}
              {'  '}"score": 664,{'\n'}
              {'  '}"reasons": [<span className="g">"R31"</span>,<span className="g">"R14"</span>] <span className="k">{'}'}</span>
            </pre>
          </div>
          <div className="chap">
            <div className="cn">02</div>
            <div>
              <h3>Record</h3>
              <p>An append-only, hash-chained ledger stores the full normalized input and the rule version. Any decision can be re-run and proven identical, years later.</p>
            </div>
            <pre className="art" id="artLedger">
              {ART_LEDGER.map((row, i) => (
                <span key={i}>
                  {i > 0 && '\n'}
                  <span className="k">#{String(row.seq).padStart(2, '0')}</span> prev{' '}
                  <span className="k">{row.prev.slice(0, 6)}…</span>
                  {'\n    → '}
                  <span className="g">{row.hash.slice(0, 10)}…</span>
                </span>
              ))}
            </pre>
          </div>
          <div className="chap">
            <div className="cn">03</div>
            <div>
              <h3>Replay</h3>
              <p>Draft a rule change and replay every past decision through it: approval shifts, flipped applicants, exposure-weighted loss — segment by segment.</p>
            </div>
            <pre className="art" id="artReplay">
              <span className="k">cutoff{'  '}</span> 680 → <span className="g">700</span>
              {'\n'}
              <span className="k">approval</span> {pct(art.la, 1)} → <span className="g">{pct(art.ca, 1)}</span>
              {'\n'}
              <span className="k">flipped </span> {fmt(art.fl)}
              {'\n'}
              <span className="k">unknown </span> {art.unk}
            </pre>
          </div>
        </div>
      </section>

      <section className="sec wide" id="audit">
        <div className="wide-head">
          <div>
            <div className="kick">Five-line audit</div>
            <h2>
              How honest is your
              <br />
              <em>credit stack?</em>
            </h2>
          </div>
          <p>For strategy and risk teams. Pick the answer that's true today, not the one on the roadmap.</p>
        </div>
        <AuditQuiz onOpen={() => navigate('/login')} />
      </section>

      <section className="sec wide" id="rules" style={{ paddingTop: 40 }}>
        <div className="wide-head">
          <div>
            <div className="kick">The fine print</div>
            <h2>
              Four numbers every
              <br />
              decision <em>must respect.</em>
            </h2>
          </div>
          <p>Consumer credit sits inside some of the most specific rules in finance. Parallax builds them into the engine, not a checklist.</p>
        </div>
        <div className="figs">
          <a className="fig" href="https://www.consumerfinance.gov/rules-policy/regulations/1002/9/" target="_blank" rel="noopener">
            <b>30 days<sup>1</sup></b>
            <p>to notify an applicant of adverse action after a completed application.</p>
          </a>
          <a className="fig" href="https://www.consumerfinance.gov/rules-policy/regulations/1002/9/" target="_blank" rel="noopener">
            <b>4 reasons<sup>2</sup></b>
            <p>is where Reg B guidance says listing more stops helping the applicant.</p>
          </a>
          <a className="fig" href="https://www.consumerfinance.gov/rules-policy/regulations/1026/51/" target="_blank" rel="noopener">
            <b>Under 21<sup>3</sup></b>
            <p>applicants need an independent ability to pay, or a co-signer.</p>
          </a>
          <a className="fig" href="https://www.ftc.gov/legal-library/browse/statutes/fair-credit-reporting-act" target="_blank" rel="noopener">
            <b>60 days<sup>4</sup></b>
            <p>to request a free copy of the credit report used in the decision.</p>
          </a>
        </div>
        <ol className="fns">
          <li>
            ¹ <a href="https://www.consumerfinance.gov/rules-policy/regulations/1002/9/" target="_blank" rel="noopener">ECOA · Regulation B §1002.9 ↗</a>
          </li>
          <li>
            ² <a href="https://www.consumerfinance.gov/rules-policy/regulations/1002/9/" target="_blank" rel="noopener">Regulation B · official commentary ↗</a>
          </li>
          <li>
            ³ <a href="https://www.consumerfinance.gov/rules-policy/regulations/1026/51/" target="_blank" rel="noopener">CARD Act 2009 · Regulation Z §1026.51 ↗</a>
          </li>
          <li>
            ⁴ <a href="https://www.ftc.gov/legal-library/browse/statutes/fair-credit-reporting-act" target="_blank" rel="noopener">Fair Credit Reporting Act ↗</a>
          </li>
        </ol>
      </section>

      <section className="gov" id="gov">
        <div className="gov-in">
          <div>
            <div className="kick" style={{ color: '#a79c89' }}>
              Governance built in
            </div>
            <blockquote style={{ marginTop: 28 }}>
              “The person who proposes a rule change can <em>never</em> be the person who approves it.”
              <cite>— MAKER-CHECKER, ENFORCED IN THE ENGINE</cite>
            </blockquote>
          </div>
          <div className="govl">
            <div>
              <b>One-step rollback</b>
              <span>Promotions and rollbacks are both written to the ledger, with who did what.</span>
            </div>
            <div>
              <b>Adverse action, by template</b>
              <span>Reason codes ranked by points lost, capped at four, rendered deterministically. No model in the loop.</span>
            </div>
            <div>
              <b>Drift you can see</b>
              <span>Population Stability Index against the development baseline, with watch and investigate thresholds.</span>
            </div>
            <div>
              <b>A careful assistant</b>
              <span>Reads and explains with read-only credentials. Treats data as data. Can never make a decision.</span>
            </div>
          </div>
        </div>
      </section>

      <section className="end">
        <div className="kick">Two views · one decision</div>
        <h2>
          Move the line.
          <br />
          <em>Keep the receipts.</em>
        </h2>
        <button className="pill" onClick={() => navigate('/login')}>
          Open the workspace →
        </button>
      </section>
      <footer className="foot">
        <span>© 2026 Parallax · Portfolio project · All data is synthetic</span>
        <span>Privacy&nbsp;&nbsp;&nbsp;&nbsp;Terms</span>
      </footer>
    </div>
  )
}

function AuditQuiz({ onOpen }: { onOpen: () => void }) {
  const [answers, setAnswers] = useState<(number | null)[]>(QZ.map(() => null))
  const answered = answers.filter((x) => x != null).length
  const sc = answers.reduce<number>((s, k, i) => s + (k != null ? QZ[i].o[k][1] : 0), 0)
  const [lab, txt] =
    sc >= 9
      ? ['Well governed', 'Your process already covers most of what regulators and risk committees ask for.']
      : sc >= 5
        ? ['Some exposure', 'A few gaps could turn a routine rule change or audit question into a scramble.']
        : ['Significant exposure', 'Rule changes and audits are likely running on trust rather than evidence.']

  function answer(i: number, k: number) {
    setAnswers((prev) => prev.map((v, j) => (j === i ? k : v)))
  }

  return (
    <div className="qn">
      <div className="qh">
        <div>QUESTION</div>
        <div>STRONG</div>
        <div>PARTIAL</div>
        <div>GAP</div>
      </div>
      <div id="qRows">
        {QZ.map((q: Quiz, i) => {
          const a = answers[i]
          const p = a != null ? q.o[a][1] : null
          return (
            <div className="qr" key={i}>
              <div className="qq">
                <span>0{i + 1}</span>
                <div>
                  <b>{q.q}</b>
                  <small>{q.d}</small>
                  {p != null && p < 2 && <span className="qhelp">→ Parallax: {q.f}</span>}
                </div>
              </div>
              {q.o.map((o, k) => (
                <button key={k} className={`qo ${a === k ? 'on' : ''}`} onClick={() => answer(i, k)}>
                  <i />
                  {o[0]}
                </button>
              ))}
            </div>
          )
        })}
      </div>
      <div className="qtot" id="qTot">
        <div className="sc">
          {sc}
          <em> / 10</em>
        </div>
        <div className="row">
          <div style={{ flex: 1, minWidth: 240 }}>
            {answered < QZ.length ? (
              <>
                <h4>
                  {QZ.length - answered} line{QZ.length - answered > 1 ? 's' : ''} to go
                </h4>
                <p>Your total appears here as you answer.</p>
              </>
            ) : (
              <>
                <h4>{lab}.</h4>
                <p>{txt}</p>
              </>
            )}
          </div>
          {answered === QZ.length && (
            <>
              <button className="pill" onClick={onOpen}>
                See it working →
              </button>
              <button className="tlink" onClick={() => setAnswers(QZ.map(() => null))}>
                Reset
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
