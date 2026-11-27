# Progress

- November 26, 2026 IST. Verified the official rules and schedule. Created `codex/qa-november` from the existing README-only repository.
- Docker Desktop and Maven Central are reachable with scoped tool approval. No configured hosted AI credentials were found. Local Flagsmith and Ollama are the intended verification targets.
- Implemented the first backend milestone in `e50b3f9`. The dashboard, deployment, tests and documentation are in the QA milestone.
- The wrapper and Java tests pass, including simultaneous claims. The dashboard build and three Playwright fixture scenarios pass.
- Verified real isolated Flagsmith identity evaluation and the 40% experimental allocation.
- Built the Compose stack from source and verified its dashboard, Swagger and application health endpoints. The isolated PostgreSQL services and Flagsmith migrations started successfully.
- Migrated to Spring Boot 4.2 and Jackson 3 in `dca6a94` after the Java dependency review. Both the manifest scan and an OSV query of 88 resolved Maven runtime packages found no remaining advisories. Maven verified 14 tests and Playwright verified 3 browser scenarios.
- Qwen 4B exceeded the CPU deadline. Qwen 1.7B returned unsolicited fields in a tool call. The adapter now strips those fields and binds tool execution to the server-side incident. The evidence-grounded workflow is committed in `80bdb91`.
- The live suite passed with two native tools on the shared failure and three on the payment regression. The shared incident produced no proposal. Explicit approval disabled the real flag, and recovery used 100 new requests with zero errors.
- Saved the measured results and three screenshots in [the verification report](docs/test-report.md). The local stack is running. Test traffic is stopped, and the isolated payment flag remains disabled after verified recovery.
- The initial application milestone was published on QA and Dev. The [demo recording script](docs/demo-script.md) and [submission draft](docs/submission.md) describe the submission workflow.
- Demo rehearsal follow-up. The locally rebuilt dashboard shows current checkout health separately from stored incidents and refreshes Flagsmith inventory when its page opens. The AI adapter asks again when the model tries to conclude before reading required evidence. Revalidated with 16 Maven tests and five browser scenarios.
- Reran the reported incident against local Ollama. It read cohort metrics and the Flagsmith configuration, returned Observe because there were no healthy baseline samples, and generated zero rollback proposals.
- Updated Overview to show checkout pass rates. Rollback review requires at least two-thirds experimental server errors with healthy baseline and control evidence. Cohort counts are supplied to Ollama before its follow-up tool selection. Verified the two-call path with 17 Maven tests and checked the dashboard with five browser tests.
- November 27 demo replay. The reported Warning had zero baseline samples and correctly returned Observe with no proposal. A fresh replay recorded 40 healthy baseline checkouts, zero failures in 14 stable requests, and nine failures in 11 experimental requests. Live Ollama returned Rollback with High confidence in 38 seconds and created a pending proposal. The rehearsal did not approve it or change the flag.
