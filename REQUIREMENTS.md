# Product requirements

ReleaseGuard AI helps release teams investigate checkout regressions, compare exposed and control traffic, and reverse a suspect Flagsmith rollout only after a human reviews the exact proposal.

| Story | Acceptance |
| --- | --- |
| Developer reproduces an incident | Independently running synthetic checkout emits measured requests. Healthy, payment-specific, and shared dependency scenarios use stable identities. |
| Operator investigates | Baseline and incident windows show sample sizes, failures, P95, cohort differences, and alternative explanations. A real model chooses read-only tools and returns grounded structured output. |
| Approver decides | The proposal shows the exact environment, flag, expected configuration, desired state, and expiry. A rejected, stale, expired, or unauthorized action causes zero mutations. |
| Operator verifies | Actual Flagsmith readback and fresh telemetry determine the recovery result. Audit records preserve decisions and outcomes. |
| Judge reproduces | Compose, setup docs, scenarios and automated tests run against an isolated environment. |

The MVP includes one monitored endpoint, one mutable flag, single-environment authorization, and synthetic data. Native experimentation and additional providers are optional. Real payment networks, production credentials, and multi-tenancy are excluded. Track 02 judges review use of Flagsmith, developer impact, creativity, and the working demo.
