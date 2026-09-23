import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../services/api'
import type { CaseItem, DashboardSummary } from '../types'
import { ArrowUpRight, Activity, Database, Network, ShieldAlert, FolderOpen, CircleOff } from 'lucide-react'

type ActiveSession = {
  active: boolean
  caseNumber?: string
  caseTitle?: string
}

export default function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [cases, setCases] = useState<CaseItem[]>([])
  const [activeSession, setActiveSession] = useState<ActiveSession | null>(null)

  useEffect(() => {
    Promise.all([
      api.get('/dashboard/summary'),
      api.get('/cases'),
      api.get('/sessions/active')
    ])
      .then(([summaryResponse, casesResponse, sessionResponse]) => {
        setSummary(summaryResponse.data)
        setCases(casesResponse.data.slice(0, 5))
        setActiveSession(sessionResponse.data)
      })
      .catch(() => {
        setActiveSession({ active: false })
      })
  }, [])

  const hasActiveCase = activeSession?.active === true
  const total = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].reduce(
    (value, priority) => value + (summary?.priorityCounts?.[priority] || 0),
    0
  )

  return (
    <div>
      <div className="page-head">
        <div>
          <div className="eyebrow">COMMAND OVERVIEW</div>
          <h1>Operational intelligence</h1>
          <p>Live case, network and data posture from authorized system records.</p>
        </div>
        <div className="head-actions">
          {hasActiveCase ? (
            <span className="pill green">ACTIVE CASE · {activeSession?.caseNumber}</span>
          ) : (
            <span className="pill"><CircleOff size={14} /> NO CASE ACTIVE</span>
          )}
          <Link className="primary" to="/cases">
            Open cases <ArrowUpRight size={15} />
          </Link>
        </div>
      </div>

      {!hasActiveCase && (
        <div className="notice" role="status">
          <CircleOff size={16} />
          <div>
            <b>No case active</b>
            <span>Command Overview has no active investigation context. Start an investigation from Case Files to load case-specific intelligence.</span>
          </div>
        </div>
      )}

      <div className="metric-grid">
        {[
          ['ENTITIES', summary?.entitiesIdentified || 0, Network],
          ['CONNECTIONS', summary?.activeConnections || 0, Activity],
          ['ACTIVE CASES', summary?.activeCases || 0, FolderOpen],
          ['SOURCE RECORDS', summary?.sourceRecords || 0, Database]
        ].map(([label, value, Icon]: any) => (
          <div className="metric" key={label as string}>
            <div className="metric-icon"><Icon size={18} /></div>
            <div className="metric-label">{label}</div>
            <div className="metric-value">{hasActiveCase ? value : 'No info'}</div>
            <div className="metric-note">{hasActiveCase ? 'Database-backed' : 'No active case'}</div>
          </div>
        ))}
      </div>

      <div className="dashboard-grid">
        <section className="panel">
          <div className="panel-head">
            <div><h2>Case priority distribution</h2><small>{hasActiveCase ? `${total} total cases` : 'No active case selected'}</small></div>
            <ShieldAlert size={18} />
          </div>
          {hasActiveCase ? (
            <div className="bars">
              {['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map(priority => (
                <div className="bar-row" key={priority}>
                  <span>{priority}</span>
                  <div className="bar">
                    <i style={{ width: `${total ? (summary?.priorityCounts?.[priority] || 0) / total * 100 : 0}%` }} />
                  </div>
                  <b>{summary?.priorityCounts?.[priority] || 0}</b>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty">No information available until an investigation case is active.</div>
          )}
        </section>

        <section className="panel">
          <div className="panel-head">
            <div><h2>Recent cases</h2><small>Most recently updated investigations</small></div>
            <Link className="linkish" to="/cases">View all</Link>
          </div>
          {cases.length ? cases.map(item => (
            <Link className="row-card" to={`/cases/${item.id}`} key={item.id}>
              <div><b>{item.title}</b><small>{item.caseNumber} · {item.priority}</small></div>
              <ArrowUpRight size={15} />
            </Link>
          )) : <div className="empty">No cases registered.</div>}
        </section>
      </div>
    </div>
  )
}
