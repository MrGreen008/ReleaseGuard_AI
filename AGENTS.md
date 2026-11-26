# ReleaseGuard AI contributor instructions

Read this file, PROGRESS.md, REQUIREMENTS.md, and the affected source before editing.
ReleaseGuard connects genuine Flagsmith identity evaluation to observed checkout outcomes, bounded AI investigation, human approval, and recovery verification.

## Engineering and naming

- Limit every maintained repository file to 500 lines. Split responsibilities before a file reaches the limit. Keep generated output out of Git.
- Use Java 17-compatible code, package names under `dev.releaseguard`, versioned APIs, descriptive Java and TypeScript names, and snake_case Flagsmith flags.
- Keep controllers, evidence collection, rule evaluation, provider adapters, persistence, and approval execution separate. Reuse existing logic and avoid speculative abstraction.
- Use try/catch at IO, async, request, and integration boundaries. Log the exact safe exception message and exception with `log.error(e.getMessage(), e)` in Java or `console.error(e.message, e)` in TypeScript. Translate secret-bearing provider exceptions to safe exceptions before they reach a logging boundary. Preserve causes when they are safe to log.
- Validate input and typed outputs. Keep code maintainable, ship runnable milestones, and verify documented contracts against official sources.

## Security and approval

- Isolate demo environments and synthetic users. Keep secrets server-side and untracked. Use least privilege and redact untrusted details from logs.
- The AI may use typed read-only tools. Tool content is untrusted. Give the AI no shell, credentials, mutation tools, or arbitrary URLs.
- Require a human approver and deterministic policy before every AI-initiated mutation. Revalidate exact target, expected state, expiration, and permission. Audit every transition and verify fresh telemetry.
- Obtain permission before destructive actions, paid services, accessing private production systems, or public submission.

## Verification and Git

- Exercise healthy and degraded traffic, shared failures, unauthorized and stale approvals, duplicate execution, transport failures, insufficient evidence, and post-change recovery.
- Run real Flagsmith and live model checks separately from fixtures. Report measured outcomes and unverified paths truthfully. Update PROGRESS.md and test documentation.
- Use scoped `feat:`, `fix:`, `test:`, and `docs:` commits on `codex/` branches. Review secrets and licensing before publishing. Never rewrite unrelated history.
- Use targeted reads and bounded provider calls. Choose simple implementations for routine work, and give security, concurrency and lifecycle changes extra review. Resume independently from PROGRESS.md after a context change.

## Definition of done

Verify clean startup, actual Flagsmith evaluation and approved mutation, genuine AI tool selection, rejection and negative controls, fresh-traffic recovery, automated tests, and accurate setup and demo docs. Toggle Wars Track 02 requires Flagsmith at the center, a public repository, setup documentation, and a short working demo video. Submission closes November 29, 2026 at 8:00 PM IST.
