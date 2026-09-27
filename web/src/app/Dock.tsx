import { Fragment } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { NAV_GROUPS, isNavActive } from './nav'
import { useReviewQueue, useVersions } from '../api/hooks'

export function Dock() {
  const location = useLocation()
  const navigate = useNavigate()
  const { data: queue } = useReviewQueue({ refetchInterval: 30000 })
  const { data: versions } = useVersions()
  const queueCount = queue?.length ?? 0
  const hasProposed = versions?.items?.some((v) => v.status === 'PROPOSED') ?? false

  return (
    <nav className="dock">
      {NAV_GROUPS.map((group, gi) => (
        <Fragment key={gi}>
          {gi > 0 && <span className="dsep" />}
          {group.map((item) => (
            <div
              key={item.key}
              className={`dk ${isNavActive(location.pathname, item) ? 'on' : ''}`}
              onClick={() => navigate(item.path)}
            >
              <small>{item.idx}</small>
              <span>{item.label}</span>
              {item.key === 'queue' && queueCount > 0 && <em>{queueCount}</em>}
              {item.key === 'lab' && hasProposed && <em>1</em>}
            </div>
          ))}
        </Fragment>
      ))}
    </nav>
  )
}
