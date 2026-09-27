import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageHeader, Card } from '../ui/components'
import { useAssistantStatus, useAssistantTools, useAssistantChat, useVersions } from '../api/hooks'
import { useAuth } from '../app/AuthProvider'
import { sanitizeInline } from '../ui/safeMarkup'
import type { ToolCall } from '../api/types'

interface Msg {
  role: 'u' | 'a' | 'tool'
  text: string
  error?: boolean
}

function argsStr(args: unknown): string {
  if (args == null) return ''
  if (typeof args === 'string') return args
  if (typeof args === 'object') return Object.values(args as Record<string, unknown>).map((v) => JSON.stringify(v)).join(', ')
  return String(args)
}
function toolText(tc: ToolCall): string {
  const base = `⚙ ${tc.name}(${argsStr(tc.arguments)})`
  if (tc.error) return `${base} → ${tc.error}`
  return tc.summary ? `${base} → ${tc.summary}` : base
}
// Render assistant text safely: escape, then re-permit **bold** and line breaks only.
function renderAssistant(text: string): string {
  let s = sanitizeInline(text) // escapes, keeps <b> only (there is no literal <b> here, so all escaped)
  s = s.replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
  s = s.replace(/\n/g, '<br>')
  return s
}

export function Assistant() {
  const [params] = useSearchParams()
  const { me } = useAuth()
  const { data: status, isError } = useAssistantStatus()
  const { data: tools } = useAssistantTools()
  const { data: versions } = useVersions()
  const chat = useAssistantChat()

  const [messages, setMessages] = useState<Msg[]>([])
  const [input, setInput] = useState('')
  const [conversationId, setConversationId] = useState<string | undefined>(undefined)
  const [typing, setTyping] = useState(false)
  const msgsRef = useRef<HTMLDivElement>(null)
  const askedRef = useRef(false)

  const configured = !!status?.configured && !isError
  const first = (me?.displayName || '').split(' ')[0]

  const suggestions = useMemo(() => {
    const draft = versions?.items?.find((v) => v.status !== 'LIVE' && v.status !== 'RETIRED' && v.latestReplayJobId)
    return [
      'Why was APP-1041 declined?',
      'Summarize APP-1053',
      draft ? `Why did approvals drop under ${draft.version}?` : 'Compare the live version with the latest candidate',
      'Which score band has the highest override rate?',
      'Is score drift a concern?',
      'Approve APP-1043 now',
    ]
  }, [versions])

  function send(text: string) {
    const q = text.trim()
    if (!q || !configured) return
    setInput('')
    setMessages((m) => [...m, { role: 'u', text: q }])
    setTyping(true)
    chat.mutate(
      { conversationId, message: q },
      {
        onSuccess: (res) => {
          setConversationId(res.conversationId)
          const add: Msg[] = []
          ;(res.toolCalls || []).forEach((tc) => add.push({ role: 'tool', text: toolText(tc), error: !!tc.error }))
          add.push({ role: 'a', text: res.answer || '' })
          setMessages((m) => [...m, ...add])
          setTyping(false)
        },
        onError: () => {
          setMessages((m) => [...m, { role: 'a', text: "Sorry — I couldn't reach the assistant." }])
          setTyping(false)
        },
      },
    )
  }

  // ?ask= auto-sends once, when configured.
  useEffect(() => {
    const ask = params.get('ask')
    if (ask && configured && !askedRef.current) {
      askedRef.current = true
      send(ask)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [configured])

  useEffect(() => {
    if (msgsRef.current) msgsRef.current.scrollTop = msgsRef.current.scrollHeight
  }, [messages, typing])

  return (
    <>
      <PageHeader
        eyebrow="Assistant"
        title="Underwriter & strategist agent"
        description="Answers questions by calling Parallax APIs as tools. Read-only credentials, masked PII, numbers only from tool results. It can never make or change a decision."
        right={<span className="chipbox">role: ASSISTANT (read-only)</span>}
      />
      <div className="chat">
        <Card style={{ display: 'flex', flexDirection: 'column' }}>
          <div className="msgs" ref={msgsRef}>
            {messages.length === 0 && (
              <div className="msg a">
                Hi {first}. I can explain decisions, summarize replay reports, compare rule versions and report override or
                drift signals. I only have read access.
              </div>
            )}
            {messages.map((m, i) =>
              m.role === 'u' ? (
                <div className="msg u" key={i}>
                  {m.text}
                </div>
              ) : m.role === 'tool' ? (
                <div className={`tool ${m.error ? 'x' : ''}`} key={i}>
                  {m.text}
                </div>
              ) : (
                <div className="msg a" key={i} dangerouslySetInnerHTML={{ __html: renderAssistant(m.text) }} />
              ),
            )}
            {typing && <div className="msg a">…</div>}
            {!configured && (
              <div className="msg a">
                The assistant model is not configured. Set OPENAI_API_KEY for assistant-service and restart it.
              </div>
            )}
          </div>
          <div className="sugg">
            {suggestions.map((s) => (
              <button key={s} onClick={() => send(s)} disabled={!configured}>
                {s}
              </button>
            ))}
          </div>
          <div className="cin">
            <input
              placeholder="Ask about an application, a version or a replay…"
              value={input}
              disabled={!configured}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && send(input)}
            />
            <button className="btn p" onClick={() => send(input)} disabled={!configured}>
              Send
            </button>
          </div>
        </Card>

        <Card>
          <h3>Tools</h3>
          <p className="note" style={{ margin: '0 0 10px' }}>
            Spring AI @Tool methods, calling application-service with a read-only token.
          </p>
          {(tools || []).map((t) => (
            <div className="svc" key={t.name}>
              <code className="mono">{t.name}()</code>
              <span className="pill">{t.access}</span>
            </div>
          ))}
          <div className="svc">
            <code className="mono">approveApplication()</code>
            <span className="pill" style={{ color: 'var(--bad)' }}>
              not registered
            </span>
          </div>
          <div className="lbl" style={{ marginTop: 18 }}>
            Guardrails
          </div>
          <div className="vlist t-muted">
            <div>• Tool output is data, never instructions</div>
            <div>• Every figure must come from a tool result</div>
            <div>• Identity fields masked before the model sees them</div>
            <div>• 15-question eval set runs in CI</div>
          </div>
        </Card>
      </div>
    </>
  )
}
