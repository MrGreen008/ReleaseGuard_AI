# ReleaseGuard AI verification

Run on November 26, 2026 IST in the repository-owned Docker Desktop lab. Java compilation targets release 17. Guardian and Shop use a Java 17 runtime image.

| Check | Result |
| --- | --- |
| `mvnw.cmd -B -ntp verify` | 14 tests passed. Flyway migration, H2-backed authorization and transaction tests, telemetry aggregation, rollback policy, recovery classification, and Ollama protocol validation. |
| `npm.cmd --prefix frontend run lint` | Passed. |
| `npm.cmd --prefix frontend run build` | Type checking and production bundle passed. |
| `npm.cmd --prefix frontend test` | 3 Playwright tests passed in Edge with API fixtures. Covered explicit decision confirmation, viewer access, sign-in errors, and mobile overflow. |
| `docker compose --env-file .env --env-file .env.flagsmith config --quiet` | Passed. |
| `docker compose --env-file .env --env-file .env.flagsmith build guardian dashboard` | Java and dashboard images built. |
| `npm audit` and OSV dependency checks | No advisories found in the final manifests or among 88 queried Maven runtime packages. |
| Gitleaks and source line count | Scanned repository history. Maintained text files are at most 500 lines. |

The initial browser test caught a label capitalization error, which was fixed. The first Java dependency audit prompted a move from Spring Boot 3.5 to 4.2 and a rescan. Qwen 4B exceeded the first 120-second deadline and a follow-up 240-second deadline on this CPU. The default was changed to Qwen 1.7B with compact evidence, a bounded 240-second deadline and a warm model between nearby requests.

## Live Flagsmith and AI run

`pwsh -NoProfile -File scripts/live-test.ps1 -Capture` exited successfully. The stack used self-hosted Flagsmith 2.263.0, PostgreSQL, and Ollama with Qwen 3 1.7B.

| Scenario | Observed result |
| --- | --- |
| Healthy baseline | 34 initial checkout requests across both evaluated cohorts, with zero server errors. |
| Shared dependency outage | 14 control failures and 10 experimental failures. The model selected `compare_cohorts` and `get_dependency_health`, identified the shared dependency, and produced no rollback proposal. |
| Payment regression | The prior healthy window contained 484 requests. The incident window had 0 failures in 10 control requests and 3 failures in 4 experimental requests. The model selected three real read-only tools. Flagsmith still showed the rollout enabled while the proposal awaited approval. |
| Approved rollback | The explicit approver request changed the real payment flag from enabled to disabled. Guardian checked Flagsmith readback before opening recovery verification. |
| Verified recovery | 100 post-readback checkout requests, zero server errors, and 182 ms P95. Guardian recorded `RECOVERED`. |

Edge cases such as rejection, expired and unauthorized decisions, stale configuration, duplicate and concurrent requests, provider failure, and incomplete recovery were exercised in automated fixture tests. GitHub Actions requires a published branch to run remotely. The submission video is represented by the recording script.

The model's free-text findings can be imprecise. In the captured payment investigation it described the expected improvement as a drop in control failures, although the control cohort already had zero failures. The measured evidence, exact proposal, readback and recovery classification are independent of that wording.

[Overview](screenshots/overview.png) · [Investigation](screenshots/investigation.png) · [Recovery](screenshots/recovery.png)
