# Decisions

- Build a Java 17 Spring Boot application with separate Guardian and Shop runtime profiles so the simulator sends real HTTP requests across the service boundary.
- Use the published Flagsmith REST APIs so management state and fresh identity evaluations can be checked independently. Use one isolated environment and a multivariate payment feature. Disable the environment default to recover.
- Use Spring JDBC and Flyway for explicit transactional transitions and an append-only audit table. Keep telemetry bounded and preserve evidence with the incident.
- Use Ollama's tool protocol for local inference. All tools are read-only. A deterministic policy independently checks the evidence and configuration before issuing a rollback proposal.
