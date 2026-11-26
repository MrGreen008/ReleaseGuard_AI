package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class EvidenceFixtures {
    private EvidenceFixtures() {}

    static FlagState enabledFlag() {
        return new FlagState(12, 14, PAYMENT, true, "stable",
            List.of(new Variant("experimental", 40)), 0, "initial-revision");
    }

    static FlagState disabledFlag() {
        return new FlagState(12, 14, PAYMENT, false, "stable",
            List.of(new Variant("experimental", 40)), 0, "after-disable");
    }

    static Incident incident(Instant at) {
        return new Incident(UUID.randomUUID(), at, Severity.WARNING,
            window(at.minusSeconds(20), stats(14, 0, 60), stats(10, 0, 65)),
            window(at, stats(14, 0, 60), stats(10, 8, 350)));
    }

    static Window window(Instant end, Stats stable, Stats variant) {
        return window(end, stable, variant, stats(0, 0, 0));
    }

    static Window window(Instant end, Stats stable, Stats variant, Stats unknown) {
        long count = stable.requests() + variant.requests() + unknown.requests();
        long failed = stable.failures() + variant.failures() + unknown.failures();
        return new Window(end.minusSeconds(12), end,
            stats(count, failed, Math.max(stable.p95Ms(), Math.max(variant.p95Ms(), unknown.p95Ms()))),
            Map.of(Cohort.STABLE, stable, Cohort.EXPERIMENTAL, variant, Cohort.UNKNOWN, unknown));
    }

    static Stats stats(long count, long failures, long latency) {
        return new Stats(count, count - failures, failures, count == 0 ? 0 : (double) failures / count,
            latency, latency, Map.of(), Map.of());
    }

    static Investigation investigation(Incident incident, FlagState flag) {
        return new Investigation(incident.id(), incident.detectedAt(), "Fixture provider",
            new Finding(Recommendation.ROLLBACK, Confidence.MEDIUM, "Cohort regression", "Stable traffic is healthy",
                List.of("Shared dependency"), List.of("Observational"), "Reduce errors", "Measure new traffic"),
            List.of(), new FlagObservation(incident.detectedAt(), flag), "Test fixture");
    }
}
