# Test plan

| Area | Expected outcome |
| --- | --- |
| Baseline and low samples | Healthy traffic creates no incident. Overview returns to Normal even when an older warning stays in the queue. Sparse data creates no rollback proposal. |
| Variant regression | With a healthy baseline and control, at least two-thirds experimental server errors qualify for rollback review. A proposal has no effect before approval. |
| Shared dependency outage | Both cohorts fail and the policy denies rollback. |
| Decisions | Viewer, duplicate, expired, stale and rejected requests cannot dispatch a mutation. |
| Integrations | Real Flagsmith evaluates identities, receives an approved update, and returns verified state. Real Ollama receives cohort counts up front, selects a follow-up tool, and returns a structured finding. |
| Recovery | Fresh healthy samples recover. Persistent degradation reports failure. Sparse samples report inconclusive. |
| Provider failures | A premature conclusion triggers a bounded reminder to collect evidence. Repeated omissions, transport errors, malformed findings, and unapproved tools fail closed. |
| Installation | Run the application with the documented environment and a fresh database. |
