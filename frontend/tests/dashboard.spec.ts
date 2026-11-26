import { expect, test } from '@playwright/test'
import type { Dashboard, Flag, Integrations, Proposal, Stats, Window } from '../src/types'

const at = '2026-11-26T03:25:00Z'
const stats = (requests: number, failures: number, p95Ms: number): Stats => ({
  requests, successes: requests - failures, failures, p95Ms, avgMs: p95Ms,
  errorRate: requests ? failures / requests : 0, statuses: {}, errors: {},
})
const window: Window = {
  from: '2026-11-26T03:24:40Z', to: at, total: stats(20, 5, 340),
  cohorts: { STABLE: stats(12, 0, 64), EXPERIMENTAL: stats(8, 5, 340), UNKNOWN: stats(0, 0, 0) },
}
const flag: Flag = {
  stateId: 1, name: 'new_payment_engine', enabled: true, value: 'stable', overrides: 0,
  variants: [{ name: 'experimental', weight: 40 }],
}
const proposal: Proposal = {
  id: 'proposal-1', incidentId: 'incident-1', environment: 'releaseguard-lab',
  expected: flag, desiredEnabled: false, createdAt: at, expiresAt: at,
  requestedBy: 'approver', decidedBy: null, verifiedAt: null, status: 'PENDING_APPROVAL',
  detail: 'Disable the payment rollout.', recovery: null,
}
const integrations: Integrations = {
  ai: true, flagsmith: true, flagsmithDetail: '', provider: 'fixture', history: [], flags: [flag],
}
const overview = (user: string, proposals: Proposal[]): Dashboard => ({
  user, at, environment: 'releaseguard-lab', metrics: window,
  lab: { enabled: true, running: false, fault: 'HEALTHY', until: null, rate: 0 },
  incidents: [{ id: 'incident-1', detectedAt: at, severity: 'WARNING', baseline: window, current: window }],
  investigations: [], proposals, audit: [],
})

test('approver reviews the exact mutation and can see recovery', async ({ page }) => {
  let decisions = 0
  let dashboard = overview('approver', [proposal])
  await page.route('**/api/v1/**', async route => {
    const url = route.request().url()
    if (url.endsWith('/integrations')) return route.fulfill({ json: integrations })
    if (url.endsWith('/decision')) {
      decisions += 1
      expect(route.request().headers()['x-releaseguard-intent']).toBe('1')
      expect(route.request().postDataJSON()).toEqual({ decision: 'APPROVE' })
      dashboard = overview('approver', [{ ...proposal, decidedBy: 'approver', status: 'RECOVERED',
        detail: 'Recovered fixture', recovery: { ...window, total: stats(24, 0, 60) } }])
      return route.fulfill({ json: dashboard.proposals[0] })
    }
    return route.fulfill({ json: dashboard })
  })
  await page.goto('/')
  await page.getByLabel('Password').fill('fixture-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Release overview' })).toBeVisible()
  await expect(page.getByText('Experimental payment', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Approvals' }).click()
  await page.getByRole('button', { name: 'Review rollback' }).click()
  await expect(page.getByRole('dialog')).toContainText('new_payment_engine')
  expect(decisions).toBe(0)
  await page.getByRole('button', { name: 'Cancel' }).click()
  await expect(page.getByRole('dialog')).not.toBeVisible()
  expect(decisions).toBe(0)
  await page.getByRole('button', { name: 'Review rollback' }).click()
  await page.getByRole('button', { name: 'Approve rollback' }).click()
  await expect(page.getByText('Recovered', { exact: true })).toBeVisible()
  await expect(page.getByText('Fresh traffic')).toBeVisible()
  expect(decisions).toBe(1)
})

test('viewer can inspect a proposal without decision controls', async ({ page }) => {
  await page.route('**/api/v1/**', route => route.request().url().endsWith('/integrations')
    ? route.fulfill({ json: integrations }) : route.fulfill({ json: overview('viewer', [proposal]) }))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByLabel('Role').selectOption('viewer')
  await page.getByLabel('Password').fill('fixture-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.getByRole('button', { name: 'Approvals' }).click()
  await expect(page.getByText('Pending approval')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Review rollback' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Reject' })).toHaveCount(0)
  const overflows = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)
  expect(overflows).toBe(false)
})

test('incorrect credentials show an actionable error', async ({ page }) => {
  await page.route('**/api/v1/dashboard', route => route.fulfill({ status: 401 }))
  await page.goto('/')
  await page.getByLabel('Password').fill('incorrect')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toHaveText('Check your username and password.')
})
