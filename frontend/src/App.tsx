import { type FormEvent, type ReactNode, useCallback, useEffect, useRef, useState } from 'react'
import { api, type Credentials, dateTime, label, time } from './api'
import { AuditList, Badge, CohortComparison, FlagTable, InvestigationDetails, Mark, Metrics, ProposalDetails, SignIn } from './components'
import type { Dashboard, Fault, Integrations, Proposal } from './types'

const pages = ['Overview', 'Feature flags', 'Incidents', 'Approvals', 'Audit trail', 'Incident lab'] as const
type Page = typeof pages[number]
type Review = { proposal: Proposal; decision: 'APPROVE' | 'REJECT' }

export function App() {
  const [credentials, setCredentials] = useState<Credentials | null>(null)
  const [data, setData] = useState<Dashboard | null>(null)
  const [integrations, setIntegrations] = useState<Integrations | null>(null)
  const [page, setPage] = useState<Page>('Overview')
  const [incidentId, setIncidentId] = useState('')
  const [error, setError] = useState('')
  const [connection, setConnection] = useState('')
  const [busy, setBusy] = useState('')
  const [review, setReview] = useState<Review | null>(null)
  const reviewDialog = useRef<HTMLDialogElement>(null)
  const viewingFlags = page === 'Feature flags'

  const refresh = useCallback(async (auth: Credentials, signal?: AbortSignal) => {
    try {
      const value = await api<Dashboard>(auth, '/dashboard')
      if (!signal?.aborted) {
        setData(previous => previous && Date.parse(previous.at) > Date.parse(value.at) ? previous : value)
        setConnection('')
      }
    } catch (e) {
      if (!signal?.aborted) setConnection(message(e))
    }
  }, [])

  useEffect(() => {
    if (!credentials) return
    const activity = new AbortController()
    const auth = credentials
    let refreshing = false
    const timer = window.setInterval(async () => {
      if (refreshing) return
      refreshing = true
      try {
        await refresh(auth, activity.signal)
      } finally {
        refreshing = false
      }
    }, 3000)
    return () => {
      activity.abort()
      window.clearInterval(timer)
    }
  }, [credentials, refresh])

  useEffect(() => {
    if (!credentials) return
    const auth = credentials
    const activity = new AbortController()
    const pollDelay = viewingFlags ? 5_000 : 12_000
    let timer: number | undefined
    const connections = async () => {
      try {
        const result = await api<Integrations>(auth, '/integrations')
        if (!activity.signal.aborted) setIntegrations(result)
      } catch {
        if (!activity.signal.aborted) setIntegrations(null)
      } finally {
        if (!activity.signal.aborted) timer = window.setTimeout(() => { void connections() }, pollDelay)
      }
    }
    void connections()
    return () => {
      activity.abort()
      window.clearTimeout(timer)
    }
  }, [credentials, viewingFlags])

  useEffect(() => {
    if (review && reviewDialog.current && !reviewDialog.current.open) reviewDialog.current.showModal()
    else if (!review && reviewDialog.current?.open) reviewDialog.current.close()
  }, [review])

  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const auth = { user: String(form.get('user')), password: String(form.get('password')) }
    setBusy('signing-in')
    setError('')
    try {
      const initial = await api<Dashboard>(auth, '/dashboard')
      setData(initial)
      setCredentials(auth)
    } catch (e) {
      setError(message(e))
    } finally {
      setBusy('')
    }
  }

  async function action(path: string, activity: string, payload?: object) {
    if (!credentials || busy) return
    setBusy(activity)
    setError('')
    try {
      await api(credentials, path, 'POST', payload)
      await refresh(credentials)
    } catch (e) {
      setError(message(e))
    } finally {
      setBusy('')
      setReview(null)
    }
  }

  function signOut() {
    setCredentials(null)
    setData(null)
    setIntegrations(null)
    setError('')
    setConnection('')
    setPage('Overview')
    setIncidentId('')
  }

  if (!credentials || !data) return <SignIn onSubmit={signIn} busy={Boolean(busy)} error={error} />

  const incident = data.incidents.find(item => item.id === incidentId) ?? data.incidents[0]
  const investigation = incident && data.investigations.find(item => item.incidentId === incident.id)
  const canDecide = data.user === 'approver'
  const pending = data.proposals.filter(item => item.status === 'PENDING_APPROVAL').length
  const openIncident = (id: string) => { setIncidentId(id); setPage('Incidents') }
  const renderProposal = (proposal: Proposal) => <ProposalDetails key={proposal.id} proposal={proposal}
    canDecide={canDecide} busy={Boolean(busy)} onDecide={decision => setReview({ proposal, decision })} />
  const switchFault = (fault: Fault) => action('/lab/fault', 'Updating incident scenario…', { fault })

  return <div className="shell">
    <aside className="sidebar">
      <div className="brand"><Mark /><div><strong>ReleaseGuard AI</strong><small>Release operations</small></div></div>
      <nav aria-label="Dashboard">
        {pages.map((item, index) => <button type="button" key={item} className={page === item ? 'active' : ''}
          aria-current={page === item ? 'page' : undefined} onClick={() => setPage(item)}>
          <span className="nav-icon" aria-hidden="true">{['◫', '⚑', '◉', '✓', '≡', '◇'][index]}</span>
          {item}{item === 'Approvals' && pending > 0 && <span className="count">{pending}</span>}
        </button>)}
      </nav>
      <div className="sidebar-footer"><span className="avatar" aria-hidden="true">{data.user[0].toUpperCase()}</span>
        <div><strong>{label(data.user)}</strong><button type="button" onClick={signOut} disabled={Boolean(busy)}>Sign out</button></div></div>
    </aside>

    <main className="content">
      <header className="topbar">
        <div className="breadcrumb">Workspace <span aria-hidden="true">/</span> {page}</div>
        <div className="chips"><Badge tone={integrations?.flagsmith ? 'green' : 'neutral'}>
          Flagsmith {integrations ? integrations.flagsmith ? 'connected' : 'unavailable' : 'checking'}
        </Badge><Badge tone={integrations?.ai ? 'green' : 'neutral'}>
          AI {integrations ? integrations.ai ? 'ready' : 'unavailable' : 'checking'}
        </Badge></div>
      </header>

      <div className="inner">
        <div className="page-heading"><div><h1>{page === 'Overview' ? 'Release overview' : page}</h1>
          <p>{data.environment} <span aria-hidden="true">·</span> Demo Shop</p></div>
          <Badge tone={data.lab.running ? 'green' : 'neutral'}>
            {data.lab.running ? 'Synthetic traffic running' : 'Synthetic traffic stopped'}</Badge>
        </div>
        {error && <div className="notice" role="alert">{error}</div>}
        {connection && <div className="notice" role="alert">Dashboard refresh failed. {connection}</div>}
        {busy && <output className="working">{busy}</output>}

        {page === 'Overview' && <>
          <Metrics data={data} />
          <div className="panel-grid">
            <Panel title="Checkout cohort health" subtitle="Successful checkouts by payment variant">
              <CohortComparison data={data.metrics} showSuccess />
            </Panel>
            <Panel title="Incident queue" subtitle="Most recent detection windows">
              {data.incidents.length === 0 ? <p className="muted">No checkout degradation has been detected.</p>
                : <ul className="incident-list">{data.incidents.slice(0, 5).map(item => <li key={item.id}>
                  <button type="button" onClick={() => openIncident(item.id)}><strong>Checkout degradation</strong>
                    <small>{time(item.detectedAt)} · {item.current.total.requests} requests</small></button>
                  <Badge tone={item.severity === 'CRITICAL' ? 'red' : 'amber'}>{label(item.severity)}</Badge>
                </li>)}</ul>}
              <button type="button" className="subtle" onClick={() => setPage('Incident lab')}>Open Incident Lab <span aria-hidden="true">→</span></button>
            </Panel>
          </div>
          <Panel title="Recent activity"><AuditList events={data.audit.slice(0, 5)} /></Panel>
        </>}

        {page === 'Feature flags' && <Panel title="Environment flag inventory"><FlagTable integrations={integrations} /></Panel>}

        {page === 'Incidents' && <Panel title="Checkout investigations">
          {incident ? <>
            <label className="filter-field">Incident<select value={incident.id}
              onChange={event => setIncidentId(event.target.value)}>
              {data.incidents.map(item => <option key={item.id} value={item.id}>
                {dateTime(item.detectedAt)} · {label(item.severity)} · {item.id.slice(0, 8)}
              </option>)}
            </select></label>
            <div className="panel-grid incident-grid">
              <section aria-label="Measured cohort evidence"><h3>Measured cohort evidence</h3>
                <CohortComparison data={incident.current} baseline={incident.baseline} /></section>
              <section aria-label="Investigation findings"><h3>Investigation findings</h3>
                {investigation ? <InvestigationDetails result={investigation} /> : <>
                  <p className="muted">The model can query cohort, configuration, and dependency evidence.</p>
                  {canDecide && <button type="button" className="primary" disabled={Boolean(busy)}
                    onClick={() => void action(`/incidents/${incident.id}/investigation`, 'Running investigation…')}>
                    Run investigation</button>}
                </>}
              </section>
            </div>
            {data.proposals.filter(item => item.incidentId === incident.id).map(renderProposal)}
          </> : <p className="muted">Detected incidents will appear here.</p>}
        </Panel>}

        {page === 'Approvals' && <Panel title="Remediation decisions">
          {data.proposals.length ? data.proposals.map(renderProposal)
            : <p className="muted">Proposals will appear here after a qualifying investigation.</p>}
        </Panel>}

        {page === 'Audit trail' && <Panel title="Workflow events"><AuditList events={data.audit} /></Panel>}

        {page === 'Incident lab' && <div className="panel-grid">
          <Panel title="Synthetic checkout traffic" subtitle="Requests go to the running Demo Shop.">
            {data.lab.enabled && canDecide ? <>
              <div className="button-row">
                <button type="button" className="primary" disabled={Boolean(busy) || data.lab.running}
                  onClick={() => void action('/lab/traffic', 'Starting traffic…', { rate: 12, seconds: 600, seed: 18 })}>
                  Start traffic</button>
                <button type="button" disabled={Boolean(busy) || !data.lab.running}
                  onClick={() => void action('/lab/traffic/stop', 'Stopping traffic…')}>Stop traffic</button>
              </div>
              <p className="muted">{data.lab.running && data.lab.until ? `Scheduled to stop at ${time(data.lab.until)}`
                : 'Starts a 10-minute session targeting 12 requests per second.'}</p>
            </> : <p className="muted">{data.lab.enabled ? 'An approver can control synthetic traffic.' : 'Incident Lab is disabled.'}</p>}
          </Panel>
          <Panel title="Fault injection" subtitle="The running traffic measures the selected fault.">
            <p>Current mode · <strong>{label(data.lab.fault)}</strong></p>
            {data.lab.enabled && canDecide && <div className="lab-actions">
              <button type="button" disabled={Boolean(busy) || data.lab.fault === 'HEALTHY'} onClick={() => void switchFault('HEALTHY')}>Healthy baseline</button>
              <button type="button" disabled={Boolean(busy) || data.lab.fault === 'PAYMENT_REGRESSION'}
                onClick={() => void switchFault('PAYMENT_REGRESSION')}>Payment regression</button>
              <button type="button" disabled={Boolean(busy) || data.lab.fault === 'DEPENDENCY_FAILURE'}
                onClick={() => void switchFault('DEPENDENCY_FAILURE')}>Shared dependency failure</button>
            </div>}
          </Panel>
        </div>}

        <footer>Last dashboard update <time dateTime={data.at}>{time(data.at)}</time></footer>
      </div>
    </main>

    <dialog aria-labelledby="decision-heading" ref={reviewDialog} onClose={() => setReview(null)}>
      {review && <>
        <h2 id="decision-heading">{review.decision === 'APPROVE' ? 'Confirm flag rollback' : 'Reject rollback'}</h2>
        <p><code>{review.proposal.expected.name}</code> in <strong>{review.proposal.environment}</strong></p>
        <p>{review.decision === 'APPROVE' ? 'Set the environment default to disabled after rechecking the approved configuration.'
          : 'Record a rejection and leave the flag state as it is.'}</p>
        <div className="button-row">
          <button type="button" onClick={() => setReview(null)} disabled={Boolean(busy)}>Cancel</button>
          <button type="button" className="primary" disabled={Boolean(busy)}
            onClick={() => void action(`/proposals/${review.proposal.id}/decision`, 'Recording decision…',
              { decision: review.decision })}>{busy ? 'Recording…'
                : review.decision === 'APPROVE' ? 'Approve rollback' : 'Confirm rejection'}</button>
        </div>
      </>}
    </dialog>
  </div>
}

function Panel({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return <section className="panel"><div className="panel-header"><h2>{title}</h2>
    {subtitle && <p>{subtitle}</p>}</div>{children}</section>
}

const message = (error: unknown) => error instanceof Error ? error.message : 'The request could not be completed.'
