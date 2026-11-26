export type Cohort = 'STABLE' | 'EXPERIMENTAL' | 'UNKNOWN'
export type Fault = 'HEALTHY' | 'PAYMENT_REGRESSION' | 'DEPENDENCY_FAILURE'
export type Stats = {
  requests: number; successes: number; failures: number; errorRate: number
  avgMs: number; p95Ms: number; statuses: Record<string, number>; errors: Record<string, number>
}
export type Window = { from: string; to: string; total: Stats; cohorts: Record<Cohort, Stats> }
export type Incident = { id: string; detectedAt: string; severity: string; baseline: Window; current: Window }
export type Flag = {
  stateId: number; name: string; enabled: boolean; value: string; overrides: number
  variants: { name: string; weight: number }[]
}
export type Observation = { at: string; state: Flag }
export type Finding = {
  recommendation: string; confidence: string; summary: string; rationale: string
  alternatives: string[]; limitations: string[]; expectedEffect: string; verification: string
}
export type Investigation = {
  incidentId: string; at: string; provider: string; finding: Finding
  tools: { name: string; at: string }[]; flag: Observation | null; policy: string
}
export type Proposal = {
  id: string; incidentId: string; environment: string; expected: Flag; desiredEnabled: boolean
  createdAt: string; expiresAt: string; requestedBy: string; decidedBy: string | null
  verifiedAt: string | null; status: string; detail: string; recovery: Window | null
}
export type Audit = { id: number; at: string; actor: string; event: string; reference: string; detail: string }
export type Lab = { enabled: boolean; running: boolean; fault: Fault; until: string | null; rate: number }
export type Dashboard = {
  at: string; environment: string; user: string; metrics: Window; lab: Lab
  incidents: Incident[]; investigations: Investigation[]; proposals: Proposal[]; audit: Audit[]
}
export type Integrations = {
  flagsmith: boolean; flagsmithDetail: string; ai: boolean; provider: string; flags: Flag[]; history: Observation[]
}
