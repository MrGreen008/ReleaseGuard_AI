package dev.releaseguard;

import static dev.releaseguard.Domain.*;
import static dev.releaseguard.EvidenceFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IncidentRulesTest {
    private final Instant now = Instant.parse("2026-11-26T03:30:00Z");
    private final IncidentRules rules = new IncidentRules(12, 4, 0.20, 250);

    @Test
    void healthyBaselineAndSparseEvidenceDoNotTriggerRollback() {
        var incident = incident(now);
        assertThat(rules.degraded(incident.baseline(), incident.baseline())).isFalse();
        var sparse = window(now, stats(4, 0, 40), stats(2, 2, 400));
        assertThat(rules.degraded(incident.baseline(), sparse)).isFalse();
        assertThat(rules.rollbackBlock(new Incident(incident.id(), now, incident.severity(),
            incident.baseline(), sparse), enabledFlag())).contains("samples");
    }

    @Test
    void affectedVariantQualifiesButSharedFailureDoesNot() {
        var incident = incident(now);
        assertThat(rules.degraded(incident.baseline(), incident.current())).isTrue();
        assertThat(rules.rollbackBlock(incident, enabledFlag())).isNull();
        var shared = window(now, stats(12, 12, 120), stats(10, 10, 120));
        assertThat(rules.degraded(incident.baseline(), shared)).isTrue();
        assertThat(rules.rollbackBlock(new Incident(incident.id(), now, Severity.CRITICAL,
            incident.baseline(), shared), enabledFlag())).contains("control");
    }

    @Test
    void latencyOnlyDetectionRequiresAnObservedBaseline() {
        var noSamples = stats(0, 0, 0);
        var warmup = window(now, stats(16, 0, 400), stats(8, 0, 400));
        assertThat(rules.degraded(window(now, noSamples, noSamples), warmup)).isFalse();
        assertThat(rules.degraded(incident(now).baseline(), warmup)).isTrue();
    }

    @Test
    void rollbackRequiresTwoThirdsExperimentalFailuresAndAHealthyBaseline() {
        var baseline = incident(now).baseline();
        var threshold = window(now, stats(16, 0, 220), stats(9, 6, 534));
        var below = window(now, stats(16, 0, 220), stats(9, 5, 534));
        var failedBaseline = window(now.minusSeconds(12), stats(16, 16, 248), stats(9, 9, 260));
        assertThat(rules.rollbackBlock(new Incident(UUID.randomUUID(), now, Severity.WARNING,
            baseline, threshold), enabledFlag())).isNull();
        assertThat(rules.rollbackBlock(new Incident(UUID.randomUUID(), now, Severity.WARNING,
            baseline, below), enabledFlag())).contains("two-thirds");
        assertThat(rules.rollbackBlock(new Incident(UUID.randomUUID(), now, Severity.WARNING,
            failedBaseline, threshold), enabledFlag())).contains("Baseline");
    }

    @Test
    void anOverrideOrAnotherFlagCannotQualify() {
        var flag = enabledFlag();
        assertThat(rules.rollbackBlock(incident(now), new FlagState(flag.stateId(), flag.featureId(), PAYMENT,
            true, flag.value(), flag.variants(), 1, flag.revision()))).contains("overrides");
        assertThat(rules.rollbackBlock(incident(now), new FlagState(flag.stateId(), flag.featureId(), "fast_checkout",
            true, flag.value(), flag.variants(), 0, flag.revision()))).isNotNull();
    }

    @Test
    void recoveryDistinguishesHealthyFailedPartialAndInconclusiveWindows() {
        var incident = incident(now);
        var empty = stats(0, 0, 0);
        assertThat(rules.recovery(incident, window(now, stats(24, 0, 60), empty))).isEqualTo(Status.RECOVERED);
        assertThat(rules.recovery(incident, window(now, stats(24, 24, 400), empty))).isEqualTo(Status.NOT_RECOVERED);
        assertThat(rules.recovery(incident, window(now, stats(24, 24, 80), empty))).isEqualTo(Status.NOT_RECOVERED);
        assertThat(rules.recovery(incident, window(now, stats(24, 3, 280), empty))).isEqualTo(Status.PARTIALLY_RECOVERED);
        assertThat(rules.recovery(incident, window(now, stats(3, 0, 60), empty))).isEqualTo(Status.INCONCLUSIVE);
        assertThat(rules.recovery(incident, window(now, stats(24, 0, 60), stats(1, 0, 40)))).isEqualTo(Status.INCONCLUSIVE);
    }

    @Test
    void aggregationUsesNearestRankAndHalfOpenWindows() {
        var telemetry = new Telemetry();
        for (int i = 1; i <= 20; i++) {
            var at = now.plusMillis(i);
            telemetry.add(new Sample(UUID.randomUUID(), "synthetic-a", CHECKOUT, at, Cohort.STABLE,
                i == 19 ? 502 : 200, i, i == 19 ? "payment_processor_failure" : "none"));
        }
        var result = telemetry.window(now, now.plusMillis(21)).total();
        assertThat(result.requests()).isEqualTo(20);
        assertThat(result.failures()).isEqualTo(1);
        assertThat(result.p95Ms()).isEqualTo(19);
        assertThat(result.statuses()).containsEntry(502, 1L);
        assertThat(telemetry.window(now, now.plusMillis(20)).total().requests()).isEqualTo(19);
    }
}
