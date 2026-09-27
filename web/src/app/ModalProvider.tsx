import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react'

interface ModalValue {
  openModal: (content: ReactNode, opts?: { locked?: boolean }) => void
  setModalContent: (content: ReactNode) => void
  setLocked: (locked: boolean) => void
  closeModal: () => void
  isOpen: boolean
}

const Ctx = createContext<ModalValue | null>(null)

const THEME_VARS = [
  '--bg',
  '--tile',
  '--card',
  '--ink',
  '--ink2',
  '--muted',
  '--line',
  '--gold',
  '--goldbg',
  '--moss',
  '--mossbg',
  '--ochre',
  '--ochrebg',
  '--rust',
  '--rustbg',
  '--plum',
  '--plumbg',
  '--code',
  '--dock',
]

/** Copy the app root's resolved theme variables onto the modal, which lives outside `.app`. */
function syncModalTheme(modal: HTMLElement) {
  const app = document.querySelector('.app')
  if (!app) return
  const cs = getComputedStyle(app)
  THEME_VARS.forEach((v) => modal.style.setProperty(v, cs.getPropertyValue(v)))
}

export function ModalProvider({ children }: { children: ReactNode }) {
  const [content, setContent] = useState<ReactNode>(null)
  const [locked, setLocked] = useState(false)
  const [open, setOpen] = useState(false)
  const modalRef = useRef<HTMLDivElement>(null)

  const openModal = useCallback((c: ReactNode, opts?: { locked?: boolean }) => {
    setContent(c)
    setLocked(!!opts?.locked)
    setOpen(true)
  }, [])
  const setModalContent = useCallback((c: ReactNode) => setContent(c), [])
  const closeModal = useCallback(() => {
    setOpen(false)
    setLocked(false)
    setContent(null)
  }, [])

  useLayoutEffect(() => {
    if (open && modalRef.current) syncModalTheme(modalRef.current)
  }, [open, content])

  useEffect(() => {
    const handler = () => {
      if (open && modalRef.current) syncModalTheme(modalRef.current)
    }
    window.addEventListener('parallax-theme', handler)
    return () => window.removeEventListener('parallax-theme', handler)
  }, [open])

  return (
    <Ctx.Provider value={{ openModal, setModalContent, setLocked, closeModal, isOpen: open }}>
      {children}
      {open && (
        <div
          className="modal-wrap"
          onClick={(e) => {
            if (e.target === e.currentTarget && !locked) closeModal()
          }}
        >
          <div className="modal" ref={modalRef}>
            {content}
          </div>
        </div>
      )}
    </Ctx.Provider>
  )
}

export function useModal(): ModalValue {
  const v = useContext(Ctx)
  if (!v) throw new Error('useModal must be used within ModalProvider')
  return v
}
