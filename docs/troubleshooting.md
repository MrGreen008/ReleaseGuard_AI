# Troubleshooting

- If Docker cannot be reached, start Docker Desktop and confirm it is running Linux containers.
- If a localhost port is occupied, stop the competing owned service or adjust the relevant Compose mapping and documented URL.
- If Flagsmith starts without the demo flag, run `pwsh -File scripts/setup.ps1 -SkipModel` against the repository's isolated services. The seed creates the flag on first setup. It preserves state on subsequent starts.
- If Flagsmith is unavailable to Guardian, verify the two ignored environment files match the current project volumes and check Flagsmith service health. Avoid printing the credential-bearing Compose configuration. Use `config --quiet`.
- If AI is unavailable, check `docker compose -f compose.dependencies.yml exec -T ollama ollama list` and verify the configured model tag. Offline monitoring remains active.
- If samples are insufficient, start healthy traffic, wait for both cohorts, and trigger a fresh scenario. After a shared outage, allow the full baseline window to fill with healthy samples.
- To replay the rollback demo, select Healthy baseline in Incident Lab and re-enable only `new_payment_engine` in the isolated Flagsmith dashboard. Lab controls themselves do not mutate the feature flag.
- Stop the project with `pwsh -File scripts/stop.ps1`. This preserves the database and model volumes. Resetting all volumes destroys local workflow history and cached models. Do it only if you intend to discard those data.
