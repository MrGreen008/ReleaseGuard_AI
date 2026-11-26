# Security model

- Run the lab against the dedicated Flagsmith instance deployed by this repository. Keep the organization API token, Shop service password, and database credentials on the server. Setup creates random values in ignored environment files.
- The dashboard uses local Basic authentication. The selected role and password stay in browser memory for the session. Viewers can inspect, and approvers can investigate, operate the lab, and decide proposals. The Shop requires a separate simulator account. Loopback is the supported deployment boundary. Use HTTPS and approved identity infrastructure before any shared deployment.
- Preserve the generated high-entropy Shop service password. The Shop verifies it at a lower BCrypt cost to avoid skewing measured checkout latency.
- Mutation routes require an explicit operation header and reject cross-site browser requests. The dashboard uses a same-origin proxy and enables no cross-origin access. Frontend findings render as plain text.
- Read-only AI tools accept registered names and use the server-bound incident. Unsolicited argument fields are stripped and malformed payloads are rejected. Structured conclusions require supporting tool evidence. Deterministic policy independently controls proposal creation and approved execution.
- Approval expires after three minutes. The executor serializes release operations, verifies exact configuration and environment immediately before writing, and checks readback. An ambiguous write is never retried automatically.
- Audit is application-level append-only. PostgreSQL administrators can alter tables. Persisted model results contain summaries and tool names rather than hidden reasoning.
- Lab controls are disabled by application default. The isolated Compose profile explicitly enables them. Neither the database ports nor Ollama are published.
- If an operation becomes uncertain, inspect the exact flag and recent Flagsmith activity manually. The dedicated Guardian stops subsequent mutations while that state exists. Preserve the audit records and use an authorized operator intervention before resuming.
- Report suspected issues through the repository owner's private reporting channel.
