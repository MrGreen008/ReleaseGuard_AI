# API overview

Guardian API paths start at `/api/v1`. The [OpenAPI UI](http://localhost:8080/swagger-ui/index.html) requires the configured viewer or approver credentials.

| Method and path | Access | Purpose |
| --- | --- | --- |
| GET /dashboard | Viewer or approver | Recent metrics, incidents, investigations, approvals, and audit |
| GET /integrations | Viewer or approver | Flagsmith inventory, observed configuration history, and configured model availability |
| POST /lab/traffic | Approver | Start synthetic traffic with `rate` (1–20), `seconds` (10–900) and `seed` |
| POST /lab/traffic/stop | Approver | End the current traffic run |
| POST /lab/fault | Approver | Select HEALTHY, PAYMENT_REGRESSION, or DEPENDENCY_FAILURE |
| POST /incidents/{id}/investigation | Approver | Run the bounded AI investigation or return the stored result |
| POST /proposals/{id}/decision | Approver | Record APPROVE or REJECT against one proposal |

POST requests require `X-ReleaseGuard-Intent: 1`. Expired, stale, rejected and ambiguous outcomes are explicit states. A repeated decision receives HTTP 409 and performs no new operation. Authentication and roles are enforced on the server. Unknown resources return 404. Integration failures report 503 with sanitized detail.

Read-only AI tools query incident cohort metrics, dependency error categories, current flag configuration, previous observed flag state, and earlier remediation results. No tool accepts raw network destinations or shell input.

Shop listens on 8081. The simulator credentials authorize the product catalog, demo orders and checkout endpoints. Shop actuator health and Guardian actuator health are public only on the loopback-published interfaces.
