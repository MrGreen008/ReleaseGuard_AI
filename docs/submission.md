# Submission draft

**ReleaseGuard AI — Track 02, AI Agents and Feature Flags**

A new payment release can raise checkout failures for an exposed cohort while control traffic remains healthy. ReleaseGuard evaluates synthetic customers through Flagsmith, measures real checkout outcomes, and gives an AI investigator bounded, read-only evidence tools. It compares stable and experimental traffic, considers shared dependency failures, and presents its recommendation with the supporting evidence.

For a qualifying feature regression, an approver reviews the exact Flagsmith rollback. ReleaseGuard checks the current configuration before applying it, reads back the flag state, and measures fresh traffic before classifying recovery. When both cohorts show a shared dependency failure, the workflow leaves the rollout alone.

The repository includes a reproducible Compose lab, run instructions, and automated safety and workflow checks. The demo video should show the actual feature regression, explicit approval, recovery, and shared failure.

Flagsmith features used include feature flags, remote config, the Flagsmith API, and self-hosted Flagsmith. ReleaseGuard records checkout metrics in its own telemetry. See the [setup guide](../README.md) and [measured verification results](test-report.md). [Official rules](https://www.wemakedevs.org/hackathons/flagsmith/rules)
