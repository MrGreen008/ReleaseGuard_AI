# Decisions

- Build a Java 17 Spring Boot application with separate Guardian and Shop runtime profiles so the simulator sends real HTTP requests across the service boundary.
- Use Spring Boot 4.2 and Springdoc 3.2.1 after the advisory scan identified known critical issues in the initial Spring Boot 3.5 dependency line. Keep Java 17 as the runtime and compilation baseline.
- Use the published Flagsmith REST APIs so management state and fresh identity evaluations can be checked independently. Use one isolated environment and a multivariate payment feature. Disable the environment default to recover.
- Use Spring JDBC and Flyway for explicit transactional transitions and an append-only audit table. Keep telemetry bounded and preserve evidence with the incident.
- Use Ollama's tool protocol for local inference. All tools are read-only. A deterministic policy independently checks the evidence and configuration before issuing a rollback proposal.
