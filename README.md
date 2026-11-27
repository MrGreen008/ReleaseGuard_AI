# ReleaseGuard AI

ReleaseGuard investigates checkout regressions during a feature rollout. Demo Shop evaluates each synthetic customer through Flagsmith. Guardian compares real HTTP outcomes by cohort, gives a local model read-only evidence tools, and requires an approver to confirm a specific rollback. It verifies the resulting state in Flagsmith and measures fresh traffic before classifying recovery.

**Toggle Wars Track 2 — AI Agents and Feature Flags.** See the [live verification results](docs/test-report.md) and [demo walkthrough](docs/demo-script.md).

## Run locally

Install Git, Docker Desktop with Linux containers, and PowerShell 7 (`pwsh`). Open Docker Desktop before running the commands below. The first start downloads container images and the configured Qwen model. No hosted AI token or paid account is required.

The first investigation on a CPU-only machine can take several minutes while Ollama loads the model. A completed investigation retains the model briefly for the next request.

```powershell
git clone --branch release/nov_Dev https://github.com/MrGreen008/ReleaseGuard_AI.git
cd ReleaseGuard_AI
pwsh -File scripts/start.ps1
```

| Service | URL |
| --- | --- |
| Dashboard | <http://localhost:3000> |
| Flagsmith | <http://localhost:8000> |
| Guardian API documentation | <http://localhost:8080/swagger-ui/index.html> |
| Demo Shop health | <http://localhost:8081/actuator/health> |

Sign in to ReleaseGuard as `approver` using `RELEASE_APPROVER_PASSWORD` from `.env`. Use `viewer` with `RELEASE_VIEWER_PASSWORD` for read-only access. For the isolated Flagsmith UI, sign in as `releaseguard-admin@example.invalid` with `FLAGSMITH_ADMIN_PASSWORD`.

Setup generates local credentials and integration configuration in the ignored `.env` and `.env.flagsmith` files. The model tag defaults to `qwen3:1.7b`. See [configuration and troubleshooting](docs/troubleshooting.md).

To stop the project and retain both databases and the model volume, run `pwsh -File scripts/stop.ps1`.

## How we use Flagsmith

- **Self-hosted Flagsmith** runs in Docker Compose with a dedicated demo environment.
- **Feature flags and remote config** use `new_payment_engine` with `stable` and `experimental` values weighted 60% and 40%. Demo Shop calls the identity evaluation API for each synthetic shopper. ReleaseGuard measures checkout outcomes in its own telemetry.
- **Flagsmith Management API** supplies the rollout configuration to the agent through a read-only tool. After a human approves the exact change, Guardian revalidates the flag, disables it, and reads it back before measuring recovery.

## Reproduce the incident

1. Confirm **New Payment Engine** is enabled in the local Flagsmith environment. After a prior rollback, re-enable the flag there before replaying.
2. In **Incident Lab**, select **Healthy baseline** and start traffic. Wait for at least 30 successful checkout requests with samples in both cohorts.
3. With traffic running, select **Payment regression**. Open the newest **Checkout degradation** in the Overview incident queue and choose **Run investigation**. A Warning can qualify for rollback.
4. A healthy baseline and stable cohort, plus at least two-thirds experimental server errors, can qualify for human rollback review. In **Approvals**, review the proposed flag change and approve it while traffic continues. Wait for **Recovered** and inspect the fresh checkout metrics.
5. For the shared-failure control, re-enable the flag in local Flagsmith, restore **Healthy baseline**, and let healthy traffic accumulate. Select **Shared dependency failure**, open the new incident, and run the investigation. Both cohorts fail when the rollout is active, and no rollback proposal is created.

An incident with insufficient healthy baseline samples returns a read-only finding. To collect new evidence, switch back to **Healthy baseline**, build fresh traffic, and trigger the scenario again. Each recorded incident retains its original measurement window.

## Verify

For local builds, install JDK 17 or newer and Node.js 22. The Maven Wrapper pins Maven. Use IntelliJ IDEA to open the root Maven project. Guardian and Shop start from the same Java artifact with separate Spring profiles.

```powershell
pwsh -File scripts/test.ps1
npm.cmd --prefix frontend exec -- playwright install chromium
npm.cmd --prefix frontend test
pwsh -File scripts/live-test.ps1
```

The live test deliberately exercises an approved mutation in the isolated lab. Start from an enabled payment flag with traffic stopped. Docker Compose provides PostgreSQL, Flagsmith and Ollama. The Java and browser fixture tests run independently of those services.

## Live screenshots

[Checkout overview](docs/screenshots/overview.png) · [AI investigation](docs/screenshots/investigation.png) · [Verified recovery](docs/screenshots/recovery.png)

## Project notes

- [Architecture and lifecycle](ARCHITECTURE.md)
- [API contracts](docs/api.md)
- [Security model and operational boundaries](SECURITY.md)
- [Test plan](TEST_PLAN.md)
- [Verification report](docs/test-report.md)
- [Demo recording script](docs/demo-script.md)
- [Track 02 submission draft](docs/submission.md)

The MVP supports one monitored checkout endpoint, one isolated Flagsmith environment, and one allowlisted rollback. The cohort comparison uses measured checkout telemetry. Native Flagsmith Experimentation and hosted production deployment are outside this MVP.

Built for [Toggle Wars, Track 02](https://www.wemakedevs.org/hackathons/flagsmith). See [LICENSE](LICENSE).
