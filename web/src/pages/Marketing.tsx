import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArchArt } from '../ui/ArchArt'

const SLIDES = [
  {
    eb: 'Credit decisioning for a moving portfolio',
    a: 'Change a rule.',
    b: 'See every decision it would have made.',
    lead: 'Replay your full decision history before a strategy ships.',
    body: 'Parallax decides credit card applications, records each decision with its exact inputs, and lets strategists test rule changes against real history — with honest loss estimates.',
  },
  {
    eb: 'A ledger you can prove',
    a: 'Every decision.',
    b: 'Reproducible, forever.',
    lead: 'Six months later, get the same answer — byte for byte.',
    body: 'An append-only, hash-chained ledger stores the full normalized input and rule version of every decision, so regulators and auditors get evidence, not a story.',
  },
  {
    eb: 'Champion and challenger',
    a: 'Two views.',
    b: 'One decision.',
    lead: "Run tomorrow's rules silently beside today's.",
    body: 'Shadow mode scores live applications under a candidate version without touching customers. Maker-checker approval and one-click rollback keep every change governed.',
  },
]

interface Q {
  q: string
  d: string
  o: [string, number][]
  f: string
}
const QZ: Q[] = [
  {
    q: 'Can you reproduce a decision from six months ago — exactly?',
    d: 'Regulators and customers ask why. Rebuilding an answer from logs is not the same as re-running the same inputs under the same rules.',
    o: [
      ['Yes, byte for byte', 2],
      ['Roughly, from logs', 1],
      ['Not really', 0],
    ],
    f: 'Decision ledger + reproduce endpoint',
  },
  {
    q: 'Before a rule change ships, do you know which past applicants it would flip?',
    d: 'Move a cutoff by twenty points and one score band can change sharply while another barely moves.',
    o: [
      ['Yes, replayed on history', 2],
      ['We estimate in a spreadsheet', 1],
      ['We find out after launch', 0],
    ],
    f: 'Strategy Lab replay',
  },
  {
    q: 'Does your loss estimate separate what you observed from what you are guessing?',
    d: 'You only see outcomes for applicants you approved. Loosening a rule approves people nobody has ever observed.',
    o: [
      ['Yes, explicitly', 2],
      ['Sometimes', 1],
      ['We treat them the same', 0],
    ],
    f: 'Reject-inference labelling',
  },
  {
    q: 'Can the person who proposes a rule change also approve it?',
    d: 'Good governance needs two sets of eyes — and a rollback that takes minutes, not a release cycle.',
    o: [
      ['No, maker-checker enforced', 2],
      ['By convention only', 1],
      ['Yes', 0],
    ],
    f: 'Maker-checker promotion + rollback',
  },
  {
    q: 'What happens to applications when your credit bureau goes down?',
    d: 'A dependency outage should degrade into a review queue, not an error page or a lost applicant.',
    o: [
      ['They queue and re-decide', 2],
      ['A manual workaround', 1],
      ['They fail', 0],
    ],
    f: 'Circuit breaker + automatic re-decision',
  },
]

function scrollToId(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

export function Marketing() {
  const navigate = useNavigate()
  const [slide, setSlide] = useState(0)
  const heroArtRef = useRef<HTMLDivElement>(null)

  // Auto-advance every 7s (and whenever the slide changes manually, restart the timer).
  useEffect(() => {
    const t = setTimeout(() => setSlide((s) => (s + 1) % SLIDES.length), 7000)
    return () => clearTimeout(t)
  }, [slide])

  const s = SLIDES[slide]

  function onHeroMove(e: React.MouseEvent<HTMLElement>) {
    const r = e.currentTarget.getBoundingClientRect()
    const dx = (e.clientX - r.left) / r.width - 0.5
    const dy = (e.clientY - r.top) / r.height - 0.5
    heroArtRef.current?.querySelectorAll<SVGGElement>('.plx').forEach((g) => {
      const d = Number(g.dataset.d || 0)
      g.style.transform = `translate(${dx * d}px,${dy * d * 0.5}px)`
    })
  }

  return (
    <div id="site">
      <header className="mnav">
        <div className="wordmark light">parallax.</div>
        <nav>
          <a onClick={() => scrollToId('how')}>Platform</a>
          <a onClick={() => scrollToId('lab')}>Strategy Lab</a>
          <a onClick={() => scrollToId('gov')}>Governance</a>
          <button className="pill-teal" onClick={() => navigate('/login')}>
            Request a demo&nbsp;&nbsp;↗
          </button>
          <a onClick={() => navigate('/login')}>Open workspace&nbsp;&nbsp;↗</a>
        </nav>
      </header>

      <section className="hero" id="hero" onMouseMove={onHeroMove}>
        <div className="hero-l" id="slide">
          <div className="fade-slide" key={slide}>
            <div className="eyeb">{s.eb}</div>
            <h1>
              {s.a}
              <em>{s.b}</em>
            </h1>
            <div className="lead">{s.lead}</div>
            <p className="body">{s.body}</p>
            <div className="cta-row">
              <button className="btn-gold" onClick={() => navigate('/login')}>
                Open the workspace <span>↗</span>
              </button>
              <button className="btn-line" onClick={() => scrollToId('how')}>
                See how it works <span>↗</span>
              </button>
            </div>
          </div>
        </div>
        <div className="hero-r" id="heroArt" ref={heroArtRef}>
          <ArchArt uid="hero" />
        </div>
        <div className="sl-ctrl">
          <button className="circ" onClick={() => setSlide((slide - 1 + SLIDES.length) % SLIDES.length)}>
            ←
          </button>
          <button className="circ" onClick={() => setSlide((slide + 1) % SLIDES.length)}>
            →
          </button>
          <div className="sl-prog" id="slProg">
            {SLIDES.map((_, i) => (
              <div key={i} className={i === slide ? 'on' : ''} onClick={() => setSlide(i)}>
                0{i + 1}
                <i />
              </div>
            ))}
          </div>
        </div>
        <div className="hint-down" onClick={() => scrollToId('check')}>
          ↓&nbsp;&nbsp;&nbsp;Take the five-question check
        </div>
      </section>

      <QuizSection onOpen={() => navigate('/login')} />

      <section className="land">
        <div>
          <div className="eyeb">The rules of the road</div>
          <h2>Every decision has a deadline.</h2>
          <p>
            Consumer credit decisions sit inside some of the most specific rules in finance. Parallax builds them into
            the engine instead of a checklist. Each figure links to the regulation behind it.
          </p>
        </div>
        <div className="lgrid">
          <a className="lcell" href="https://www.consumerfinance.gov/rules-policy/regulations/1002/9/" target="_blank" rel="noopener">
            <span className="ar">↗</span>
            <div className="lbig">30 days</div>
            <p>to notify an applicant of adverse action after a completed application</p>
            <small>ECOA · Regulation B §1002.9</small>
          </a>
          <a className="lcell" href="https://www.consumerfinance.gov/rules-policy/regulations/1002/9/" target="_blank" rel="noopener">
            <span className="ar">↗</span>
            <div className="lbig">4 reasons</div>
            <p>is where Reg B guidance says listing more stops helping the applicant</p>
            <small>Regulation B official commentary</small>
          </a>
          <a className="lcell" href="https://www.consumerfinance.gov/rules-policy/regulations/1026/51/" target="_blank" rel="noopener">
            <span className="ar">↗</span>
            <div className="lbig">Under 21</div>
            <p>applicants need an independent ability to pay, or a co-signer</p>
            <small>CARD Act 2009 · Regulation Z §1026.51</small>
          </a>
          <a className="lcell" href="https://www.ftc.gov/legal-library/browse/statutes/fair-credit-reporting-act" target="_blank" rel="noopener">
            <span className="ar">↗</span>
            <div className="lbig">60 days</div>
            <p>to request a free copy of the credit report used in the decision</p>
            <small>Fair Credit Reporting Act · adverse action</small>
          </a>
        </div>
      </section>

      <section className="msec" id="how">
        <div className="eyeb">How Parallax works</div>
        <h2>
          Decide. Record. <em>Replay.</em>
        </h2>
        <p className="sub">
          Every application runs through one deterministic engine. Every decision is written to a tamper-evident ledger
          with its exact inputs. That record is what lets you test tomorrow's rules on yesterday's applicants.
        </p>
        <div className="steps3">
          <div className="step3">
            <b>01 — DECIDE</b>
            <h3>A decision in milliseconds</h3>
            <p>
              Identity and fraud screening, legal-capacity and ability-to-pay policy, then a points-based scorecard.
              Approve, refer or decline, with a limit and ranked reason codes.
            </p>
          </div>
          <div className="step3">
            <b>02 — RECORD</b>
            <h3>Reproducible, forever</h3>
            <p>
              An append-only, hash-chained ledger stores the full normalized input and the rule version. Any decision
              can be re-run and proven identical, years later.
            </p>
          </div>
          <div className="step3">
            <b>03 — REPLAY</b>
            <h3>See before you ship</h3>
            <p>
              Draft a rule change and replay your whole history through it. Approval shifts, flipped applicants,
              exposure-weighted loss, segment by segment.
            </p>
          </div>
        </div>
      </section>

      <section className="msec" id="lab">
        <div className="labband">
          <div>
            <div className="eyeb">Strategy Lab</div>
            <h2>
              Two views. <em>One decision.</em>
            </h2>
            <p className="sub">
              Champion and challenger, side by side. Parallax separates what you observed from what you are guessing:
              applicants a looser rule would newly approve are marked outcome unknown, never silently counted as safe.
            </p>
            <div className="cta-row">
              <button className="btn-gold" onClick={() => navigate('/login')}>
                Open the Strategy Lab <span>↗</span>
              </button>
            </div>
          </div>
          <div className="labcard">
            <div className="labrow">
              <span>Candidate</span>
              <b>v1.4 · cutoff 680 → 700</b>
            </div>
            <div className="labrow">
              <span>Decisions replayed</span>
              <b>20,000</b>
            </div>
            <div className="labrow">
              <span>Approval rate</span>
              <b className="dn">−4.1 pts</b>
            </div>
            <div className="labrow">
              <span>Expected loss (observed)</span>
              <b className="up">−11.8%</b>
            </div>
            <div className="labrow" style={{ border: 0 }}>
              <span>Outcome unknown</span>
              <b>0 · tightening only</b>
            </div>
          </div>
        </div>
      </section>

      <section className="msec light" id="gov">
        <div className="eyeb">Governance built in</div>
        <h2>
          Built for how credit teams <em>actually work.</em>
        </h2>
        <div className="gov">
          <div>
            <b>Maker-checker</b>
            The person who proposes a rule change can never approve it. Rollback is one action.
          </div>
          <div>
            <b>Adverse action</b>
            Reason codes ranked by points lost, capped at four, rendered from a deterministic template.
          </div>
          <div>
            <b>Drift monitoring</b>
            Population Stability Index against the development baseline, with alert thresholds.
          </div>
          <div>
            <b>A careful assistant</b>
            An AI agent that reads and explains, with read-only credentials. It can never make a decision.
          </div>
        </div>
      </section>

      <footer className="mfoot">
        <span>© 2026 Parallax · Portfolio project · All data is synthetic</span>
        <span>Privacy&nbsp;&nbsp;&nbsp;&nbsp;Terms</span>
      </footer>
    </div>
  )
}

function QuizSection({ onOpen }: { onOpen: () => void }) {
  const [i, setI] = useState(0)
  const [answers, setAnswers] = useState<(number | undefined)[]>([])

  const done = i >= QZ.length
  const answeredCount = QZ.filter((_, k) => answers[k] != null).length

  function answerQ(k: number) {
    const next = answers.slice()
    next[i] = k
    setAnswers(next)
    setTimeout(() => {
      const all = QZ.map((_, j) => j)
      const nx = all.find((j) => j > i && next[j] == null) ?? all.find((j) => next[j] == null) ?? QZ.length
      setI(nx)
    }, 380)
  }
  function stepQ(d: number) {
    const n = i + d
    if (n < 0 || n > QZ.length) return
    if (d > 0 && answers[i] == null) return
    setI(n)
  }
  function retake() {
    setI(0)
    setAnswers([])
  }

  const score = QZ.reduce((sum, q, k) => (answers[k] != null ? sum + q.o[answers[k] as number][1] : sum), 0)
  const max = QZ.length * 2
  const [lab, txt] =
    score >= 9
      ? ['Well governed', 'Your process already covers most of what regulators and risk committees ask for.']
      : score >= 5
        ? ['Some exposure', 'A few gaps could turn a routine rule change or audit question into a scramble.']
        : ['Significant exposure', 'Rule changes and audits are likely running on trust rather than evidence.']

  const nxt = done ? 'Your result' : i + 1 < QZ.length ? QZ[i + 1].q : 'Your result'

  return (
    <section className="light-wrap" id="check">
      <div className="quiz">
        <div className="qz-top">
          <div>
            <div className="eyeb">A quick reality check</div>
            <h2>
              Five questions.<em>One clearer picture.</em>
            </h2>
          </div>
          <p>
            For credit strategy and risk teams. If any answer gives you pause, you're not alone — that's exactly the gap
            Parallax was built for.
          </p>
        </div>
        <div className="qz-body">
          <div className="qz-steps">
            {QZ.map((_, k) => (
              <span key={`n${k}`} style={{ display: 'contents' }}>
                <span
                  className={`n ${k === i ? 'on' : answers[k] != null ? 'done' : ''}`}
                  onClick={() => setI(k)}
                >
                  0{k + 1}
                </span>
                <span className="ln">
                  <i style={{ transform: `scaleX(${answers[k] != null ? 1 : k === i ? 0.18 : 0})` }} />
                </span>
              </span>
            ))}
            <span
              className={`n ${done ? 'on' : ''}`}
              onClick={() => {
                if (answeredCount === QZ.length) setI(QZ.length)
              }}
            >
              ✓
            </span>
          </div>
          {done ? (
            <div className="qz-res fade-slide">
              <div>
                <div className="qz-k">YOUR RESULT</div>
                <div className="qz-score">
                  {score}
                  <em> / {max}</em>
                </div>
                <div className="qz-q" style={{ fontSize: 40 }}>
                  {lab}.
                </div>
                <div className="qz-d">{txt}</div>
                <div className="cta-row" style={{ marginTop: 30 }}>
                  <button className="btn-gold" onClick={onOpen}>
                    Open the workspace <span>↗</span>
                  </button>
                  <button className="btn-line" style={{ color: 'var(--navy)', borderColor: 'var(--navy)' }} onClick={retake}>
                    Retake <span>↺</span>
                  </button>
                </div>
              </div>
              <div className="qz-map">
                <div className="qz-k" style={{ padding: 0, border: 0, margin: '0 0 6px' }}>
                  WHERE PARALLAX HELPS
                </div>
                {QZ.map((q, k) => {
                  const p = q.o[answers[k] as number][1]
                  return (
                    <div key={k}>
                      <span>
                        {p === 2 && <span className="ok">✓</span>} {q.q.replace(/ — exactly\?|\?$/, '')}
                      </span>
                      <b>{p === 2 ? <span className="ok">Covered</span> : q.f}</b>
                    </div>
                  )
                })}
              </div>
            </div>
          ) : (
            <div className="fade-slide" key={i}>
              <div className="qz-k">
                QUESTION 0{i + 1} / 0{QZ.length}
              </div>
              <div className="qz-q">{QZ[i].q}</div>
              <div className="qz-d">{QZ[i].d}</div>
              <div className="qz-opts">
                {QZ[i].o.map((o, k) => (
                  <button key={k} className={answers[i] === k ? 'on' : ''} onClick={() => answerQ(k)}>
                    {o[0]}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
        <div className="qz-foot">
          <div>
            <button className="circ" onClick={() => stepQ(-1)} disabled={i === 0}>
              ←
            </button>
            <button className="circ" onClick={() => stepQ(1)} disabled={done || answers[i] == null}>
              →
            </button>
          </div>
          {done ? (
            <div className="qz-next">
              <small>WHAT'S NEXT</small>
              <span>See each of these working in the workspace.</span>
            </div>
          ) : (
            <div className="qz-next">
              <small>UP NEXT</small>
              <span>{nxt}</span>
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
