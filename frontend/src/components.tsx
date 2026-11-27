import type { FormEvent, ReactNode } from 'react'
import { dateTime, label, percent, time } from './api'
import type { Audit, Dashboard, Flag, Integrations, Investigation, Proposal, Stats, Window } from './types'

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'green' | 'amber' | 'red' }) {
  return <span className={`badge ${tone}`}><i aria-hidden="true" />{children}</span>
}

export function Mark() {
  return <span className="mark" aria-hidden="true">R</span>
}

export function SignIn({ onSubmit, busy, error }: {
  onSubmit: (event: FormEvent<HTMLFormElement>) => void; busy: boolean; error: string
}) {
  return <main className="sign-in">
    <div className="sign-in-card">
      <Mark />
      <h1>ReleaseGuard AI</h1>
      <p>Sign in to the release operations console.</p>
      <form onSubmit={onSubmit}>
        <label>Role<select name="user"><option value="approver">Approver</option><option value="viewer">Viewer</option></select></label>
        <label>Password<input name="password" type="password" autoComplete="current-password" required /></label>
        {error && <p className="notice" role="alert">{error}</p>}
        <button type="submit" className="primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </div>
  </main>
}

export function Metrics({ data }: { data: Dashboard }) {
  const { total, from, to } = data.metrics
  const rate = total.requests / Math.max(1, (Date.parse(to) - Date.parse(from)) / 1000)
  const severity = data.checkoutSeverity
  return <div className="metrics">
    <Metric label="Requests" value={String(total.requests)} note={`${rate.toFixed(1)} requests / sec`} />
    <Metric label="Server error rate" value={total.requests ? percent(total.errorRate) : '—'}
      note={`${total.failures} server errors`} tone={total.failures > 0 ? 'alert-text' : undefined} />
    <Metric label="P95 latency" value={total.requests ? `${total.p95Ms} ms` : '—'}
      note={total.requests ? `${Math.round(total.avgMs)} ms average` : 'Awaiting samples'} />
    <Metric label="Current checkout health" value={severity ? label(severity) : data.checkoutSampled ? 'Normal' : 'Awaiting samples'}
      note={severity ? 'Degradation in recent traffic' : data.checkoutSampled ? 'No anomaly in recent traffic' : 'Waiting for enough checkout requests'}
      tone={severity ? 'alert-text' : undefined} />
  </div>
}

function Metric({ label: title, value, note, tone }: { label: string; value: string; note: string; tone?: string }) {
  return <div className="metric"><span>{title}</span><strong className={tone}>{value}</strong><small>{note}</small></div>
}

export function CohortComparison({ data, baseline, showSuccess = false }: {
  data: Window; baseline?: Window; showSuccess?: boolean
}) {
  const cohorts = [
    { label: 'Stable payment', stats: data.cohorts.STABLE, baseline: baseline?.cohorts.STABLE, className: 'stable' },
    { label: 'Experimental payment', stats: data.cohorts.EXPERIMENTAL,
      baseline: baseline?.cohorts.EXPERIMENTAL, className: 'experiment' },
  ]
  return <div className="cohorts">
    <div className="window-label">{time(data.from)} to {time(data.to)} <span>·</span> <code>POST /api/checkout</code></div>
    {cohorts.map(cohort => {
      const { requests, successes, errorRate } = cohort.stats
      const rate = showSuccess && requests ? successes / requests : errorRate
      return <div className="cohort" key={cohort.label}>
        <div className="cohort-heading"><strong>{cohort.label}</strong><span>{requests
          ? `${percent(rate)} ${showSuccess ? 'passed' : 'errors'}` : 'No requests'}</span></div>
        <div className={showSuccess && requests ? 'bar success-bar' : 'bar'} aria-hidden="true">
          <i className={showSuccess ? '' : cohort.className} style={{ width: percent(rate) }} /></div>
        <span className="secondary">{showSuccess && requests
          ? `${successes} passed · ${requests - successes} failed · P95 ${cohort.stats.p95Ms} ms`
          : statsLine(cohort.stats)}</span>
        {cohort.baseline && <small>Baseline · {statsLine(cohort.baseline)}</small>}
      </div>
    })}
    {data.cohorts.UNKNOWN.requests > 0 &&
      <p className="notice">{data.cohorts.UNKNOWN.requests} requests had no verified cohort.</p>}
    {Object.entries(data.total.errors).length > 0 && <dl className="errors">
      {Object.entries(data.total.errors).map(([key, value]) => <div key={key}><dt>{label(key)}</dt><dd>{value}</dd></div>)}
    </dl>}
  </div>
}

function statsLine(stats: Stats) {
  return `${stats.requests} requests · ${stats.failures} errors · P95 ${stats.requests ? `${stats.p95Ms} ms` : '—'}`
}

export function FlagTable({ integrations }: { integrations: Integrations | null }) {
  if (!integrations) return <p className="muted">Checking Flagsmith…</p>
  if (!integrations.flagsmith) return <p className="notice">{integrations.flagsmithDetail}</p>
  const payment = integrations.flags.find(flag => flag.name === 'new_payment_engine')
  return <>
    <div className="scroll-table"><table>
      <thead><tr><th scope="col">Feature</th><th scope="col">State</th><th scope="col">Configured allocation</th></tr></thead>
      <tbody>{integrations.flags.map(flag => <tr key={flag.stateId}>
        <td><code>{flag.name}</code></td><td><Badge tone={flag.enabled ? 'green' : 'neutral'}>
          {flag.enabled ? 'Enabled' : 'Disabled'}</Badge></td><td>{allocation(flag)}</td>
      </tr>)}</tbody>
    </table></div>
    {payment && !payment.enabled && <p className="muted">
      The payment rollout is disabled. Demo checkouts use the stable payment engine.
    </p>}
    {integrations.history.length > 0 && <>
      <h3>Observed configuration</h3>
      <ul className="compact-list">{integrations.history.map(item => <li key={item.at}>
        <span>{item.state.enabled ? 'Enabled' : 'Disabled'} · <code>{item.state.name}</code></span>
        <time dateTime={item.at}>{dateTime(item.at)}</time>
      </li>)}</ul>
    </>}
  </>
}

function allocation(flag: Flag) {
  if (!flag.variants.length) return '—'
  const defaults = 100 - flag.variants.reduce((sum, variant) => sum + variant.weight, 0)
  return [`${flag.value} ${percent(defaults / 100)}`,
    ...flag.variants.map(variant => `${variant.name} ${percent(variant.weight / 100)}`)].join(' · ')
}

export function InvestigationDetails({ result }: { result: Investigation }) {
  return <div className="finding">
    <div className="chips"><Badge tone={result.finding.recommendation === 'ROLLBACK' ? 'amber' : 'green'}>
      {label(result.finding.recommendation)}</Badge><Badge>{label(result.finding.confidence)} confidence</Badge></div>
    <p><strong>{result.finding.summary}</strong></p><p>{result.finding.rationale}</p>
    <dl className="details-grid">
      <div><dt>Alternative explanations</dt><dd>{result.finding.alternatives.join(' · ')}</dd></div>
      <div><dt>Evidence limits</dt><dd>{result.finding.limitations.join(' · ')}</dd></div>
      <div><dt>Expected effect</dt><dd>{result.finding.expectedEffect}</dd></div>
      <div><dt>Verification</dt><dd>{result.finding.verification}</dd></div>
    </dl>
    <p className="policy"><strong>Policy decision</strong><br />{result.policy}</p>
    <h3>Read-only tools</h3>
    <small>{result.provider}</small>
    <ul className="compact-list">{result.tools.map(tool => <li key={tool.name}><code>{tool.name}</code>
      <time dateTime={tool.at}>{time(tool.at)}</time></li>)}</ul>
    {result.flag && <p className="muted">Payment configuration observed {dateTime(result.flag.at)} · {allocation(result.flag.state)}</p>}
  </div>
}

export function ProposalDetails({ proposal, canDecide, busy, onDecide }: {
  proposal: Proposal; canDecide: boolean; busy: boolean; onDecide: (decision: 'APPROVE' | 'REJECT') => void
}) {
  const tone = proposal.status === 'RECOVERED' ? 'green' : proposal.status === 'PENDING_APPROVAL' ? 'amber'
    : ['FAILED', 'NOT_RECOVERED', 'UNCERTAIN'].includes(proposal.status) ? 'red' : 'neutral'
  return <div className="proposal">
    <div className="line"><code>{proposal.expected.name}</code><Badge tone={tone}>{label(proposal.status)}</Badge></div>
    <div className="transition"><span>{proposal.expected.enabled ? 'Enabled' : 'Disabled'}</span>
      <span aria-hidden="true">→</span><strong>{proposal.desiredEnabled ? 'Enabled' : 'Disabled'}</strong></div>
    <p className="muted">{proposal.environment} · Expires {dateTime(proposal.expiresAt)}</p>
    <p>{proposal.detail}</p>
    {proposal.decidedBy && <p className="muted">Reviewed by {proposal.decidedBy}</p>}
    {proposal.recovery && <p><strong>Fresh traffic</strong> · {statsLine(proposal.recovery.total)}</p>}
    {canDecide && proposal.status === 'PENDING_APPROVAL' && <div className="button-row">
      <button type="button" className="primary" disabled={busy} onClick={() => onDecide('APPROVE')}>Review rollback</button>
      <button type="button" disabled={busy} onClick={() => onDecide('REJECT')}>Reject</button>
    </div>}
  </div>
}

export function AuditList({ events }: { events: Audit[] }) {
  if (!events.length) return <p className="muted">Audit events appear as incidents and decisions are recorded.</p>
  return <div className="scroll-table"><table>
    <thead><tr><th scope="col">Time</th><th scope="col">Event</th><th scope="col">Actor</th><th scope="col">Details</th></tr></thead>
    <tbody>{events.map(event => <tr key={event.id}>
      <td><time dateTime={event.at}>{time(event.at)}</time></td><td><strong>{label(event.event)}</strong></td>
      <td>{event.actor}</td><td>{event.detail} <small className="muted">· {event.reference.slice(0, 8)}</small></td>
    </tr>)}</tbody>
  </table></div>
}
