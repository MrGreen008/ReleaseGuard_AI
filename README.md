# ReleaseGuard AI

**Ship confidently. Investigate intelligently. Recover safely.**

ReleaseGuard investigates checkout regressions during a feature rollout. Demo Shop evaluates each synthetic customer through Flagsmith. Guardian compares real HTTP outcomes by cohort, gives a local model read-only evidence tools, and requires an approver to confirm a specific rollback. It verifies the resulting state in Flagsmith and measures fresh traffic before classifying recovery.

## Run locally

Install Docker Desktop with Linux containers and PowerShell 7. The first start downloads container images and the configured Qwen model. No hosted AI token or paid account is required. Keep the generated `.env` and `.env.flagsmith` files private.

The first investigation on a CPU-only machine can take several minutes while Ollama loads the model. A completed investigation retains the model briefly for the next request.

```powershell
pwsh -File scripts/start.ps1
```

| Service | URL |
| --- | --- |
| Dashboard | <http://localhost:3000> |
| Flagsmith | <http://localhost:8000> |
| Guardian API documentation | <http://localhost:8080/swagger-ui/index.html> |
| Demo Shop health | <http://localhost:8081/actuator/health> |

Sign in to ReleaseGuard as `approver` using `RELEASE_APPROVER_PASSWORD` from `.env`. Use `viewer` with `RELEASE_VIEWER_PASSWORD` for read-only access. For the isolated Flagsmith UI, sign in as `releaseguard-admin@example.invalid` with `FLAGSMITH_ADMIN_PASSWORD`.

The setup script seeds a 40% experimental allocation for `new_payment_engine`, with the stable payment implementation as the default. It writes the SDK key, dedicated project and environment, explicit allowlist, and organization management token to `.env.flagsmith`. It leaves existing flag state in place on restart. The model tag comes from `OLLAMA_MODEL` and defaults to `qwen3:1.7b`. See [configuration and troubleshooting](docs/troubleshooting.md).

To stop the project and retain both databases and the model volume, run `pwsh -File scripts/stop.ps1`.

## Reproduce the incident

1. Open **Incident lab**, start traffic and wait for at least 30 healthy checkout requests across both cohorts.
2. Select **Payment regression**. Open the new incident and compare stable and experimental error rates and P95 latency.
3. Select **Run investigation**. Review the evidence, alternatives and read-only tools the model used.
4. Open **Approvals** and review the exact enabled-to-disabled change. Approve it while traffic continues. The approval first moves to verification, then shows the fresh observation outcome.
5. To try the negative control, restore Healthy baseline, re-enable the payment feature in the isolated Flagsmith UI, let the healthy window fill, and choose **Shared dependency failure**. Both cohorts fail and the policy blocks a flag rollback.

## Verify

For local builds, install JDK 17 or newer and Node.js 22. The Maven Wrapper pins Maven. Use IntelliJ IDEA to open the root Maven project. Guardian and Shop start from the same Java artifact with separate Spring profiles.

```powershell
pwsh -File scripts/test.ps1
npm.cmd --prefix frontend exec -- playwright install chromium
npm.cmd --prefix frontend test
pwsh -File scripts/live-test.ps1
```

The live test deliberately exercises an approved mutation in the isolated lab. Start from an enabled payment flag with traffic stopped. Docker Compose provides PostgreSQL, Flagsmith and Ollama. The Java and browser fixture tests run independently of those services.

## Project notes

- [Architecture and lifecycle](ARCHITECTURE.md)
- [API contracts](docs/api.md)
- [Security model and operational boundaries](SECURITY.md)
- [Test plan](TEST_PLAN.md)
- [Demo recording script](docs/demo-script.md)
- [Track 02 submission draft](docs/submission.md)

The MVP supports one monitored checkout endpoint, one isolated Flagsmith environment, and one allowlisted rollback. The cohort comparison uses measured checkout telemetry. Native Flagsmith Experimentation and hosted production deployment are outside this MVP.

Built for [Toggle Wars, Track 02](https://www.wemakedevs.org/hackathons/flagsmith). See [LICENSE](LICENSE).
