package dev.releaseguard;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Domain {
    static final String PAYMENT = "new_payment_engine";
    static final String CHECKOUT = "/api/checkout";

    private Domain() {}

    enum Cohort { STABLE, EXPERIMENTAL, UNKNOWN }
    enum Fault { HEALTHY, PAYMENT_REGRESSION, DEPENDENCY_FAILURE }
    enum Severity { WARNING, CRITICAL }
    enum Recommendation { ROLLBACK, OBSERVE, INVESTIGATE_DEPENDENCY, MANUAL_INVESTIGATION }
    enum Confidence { LOW, MEDIUM, HIGH }
    enum Status { PENDING_APPROVAL, REJECTED, EXPIRED, EXECUTING, STALE, VERIFYING,
        RECOVERED, PARTIALLY_RECOVERED, NOT_RECOVERED, INCONCLUSIVE, FAILED, UNCERTAIN }
    enum Decision { APPROVE, REJECT }

    record Sample(UUID requestId, String identity, String endpoint, Instant timestamp,
                  Cohort cohort, int status, long latencyMs, String error) {}
    record Stats(long requests, long successes, long failures, double errorRate, double avgMs,
                 long p95Ms, Map<Integer, Long> statuses, Map<String, Long> errors) {}
    record Window(Instant from, Instant to, Stats total, Map<Cohort, Stats> cohorts) {}
    record Incident(UUID id, Instant detectedAt, Severity severity, Window baseline, Window current) {}
    record FlagState(int stateId, int featureId, String name, boolean enabled, String value,
                     List<Variant> variants, int overrides, String revision) {}
    record Variant(String name, double weight) {}
    record FlagObservation(Instant at, FlagState state) {}
    record Finding(Recommendation recommendation, Confidence confidence, String summary,
                   String rationale, List<String> alternatives, List<String> limitations,
                   String expectedEffect, String verification) {}
    record ToolTrace(String name, Instant at) {}
    record Investigation(UUID incidentId, Instant at, String provider, Finding finding,
                         List<ToolTrace> tools, FlagObservation flag, String policy) {}
    record Proposal(UUID id, UUID incidentId, String environment, FlagState expected, boolean desiredEnabled,
                    Instant createdAt, Instant expiresAt, String requestedBy, String decidedBy,
                    Instant verifiedAt, Status status, String detail, Window recovery) {
        Proposal transition(Status next, String actor, Instant verified, String message, Window window) {
            return new Proposal(id, incidentId, environment, expected, desiredEnabled, createdAt,
                expiresAt, requestedBy, actor == null ? decidedBy : actor,
                verified == null ? verifiedAt : verified, next, message, window);
        }

        Proposal withStatus(Status next, String message) {
            return transition(next, null, null, message, null);
        }
    }
    record Audit(long id, Instant at, String actor, String event, UUID reference, String detail) {}
}
