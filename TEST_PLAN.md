# Test plan

| Area | Expected outcome |
| --- | --- |
| Baseline and low samples | Healthy traffic creates no incident. Sparse data creates no rollback proposal. |
| Variant regression | Both cohorts are measured and the affected cohort exceeds thresholds. Proposed rollback has no effect before approval. |
| Shared dependency outage | Both cohorts fail and the policy denies rollback. |
| Decisions | Viewer, duplicate, expired, stale and rejected requests cannot dispatch a mutation. |
| Integrations | Real Flagsmith evaluates identities, receives an approved update, and returns verified state. Real Ollama selects tools and returns structured findings. |
| Recovery | Fresh healthy samples recover. Persistent degradation reports failure. Sparse samples report inconclusive. |
| Provider failures | Transport, malformed responses and unapproved tools fail closed. |
| Installation | Run the application with the documented environment and a fresh database. |
