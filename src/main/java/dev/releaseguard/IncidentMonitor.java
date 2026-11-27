package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile("guardian")
@Component
class IncidentMonitor {
    private static final Logger log = LoggerFactory.getLogger(IncidentMonitor.class);
    private final Clock clock;
    private final Telemetry telemetry;
    private final TrafficSimulator simulator;
    private final ReleaseStore store;
    private final IncidentRules rules;
    private final long windowSeconds;
    private final long baselineSeconds;
    private boolean observingIncident;
    private volatile Instant baselineFloor = Instant.MIN;

    IncidentMonitor(Clock clock, Telemetry telemetry, TrafficSimulator simulator, ReleaseStore store, IncidentRules rules,
        @Value("${release.analysis.window-seconds}") long windowSeconds,
        @Value("${release.analysis.baseline-seconds}") long baselineSeconds) {
        if (windowSeconds < 3 || baselineSeconds < windowSeconds || baselineSeconds + windowSeconds > 900) {
            throw new IllegalArgumentException("Telemetry windows are out of range.");
        }
        this.clock = clock;
        this.telemetry = telemetry;
        this.simulator = simulator;
        this.store = store;
        this.rules = rules;
        this.windowSeconds = windowSeconds;
        this.baselineSeconds = baselineSeconds;
    }

    LiveCheckout liveCheckout() {
        Instant now = clock.instant();
        var lab = simulator.state();
        var recent = telemetry.window(now.minusSeconds(windowSeconds), now);
        var assessment = assess(now, lab, recent);
        return new LiveCheckout(recent, lab, assessment.current().total().requests() >= rules.minSamples,
            severity(assessment));
    }

    @Scheduled(fixedDelay = 2000)
    synchronized void detect() {
        try {
            Instant now = clock.instant();
            var lab = simulator.state();
            var assessment = assess(now, lab, null);
            var current = assessment.current();
            if (current.total().requests() < rules.minSamples) {
                return;
            }
            var severity = severity(assessment);
            if (severity == null) {
                observingIncident = false;
                return;
            }
            if (lab.fault() == Fault.PAYMENT_REGRESSION && lab.changedAt().plusSeconds(windowSeconds).isAfter(now)
                && (current.cohorts().get(Cohort.STABLE).requests() < rules.minCohort
                    || current.cohorts().get(Cohort.EXPERIMENTAL).requests() < rules.minCohort
                    || !rules.experimentalFailureThreshold(current.cohorts().get(Cohort.EXPERIMENTAL)))) {
                return;
            }
            if (observingIncident || store.incidents().stream().anyMatch(
                incident -> incident.detectedAt().isAfter(now.minusSeconds(8)))) {
                return;
            }
            store.recordIncident(new Incident(UUID.randomUUID(), now, severity, assessment.baseline(), current));
            observingIncident = true;
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
        }
    }

    private Assessment assess(Instant now, TrafficSimulator.State lab, Window recent) {
        Instant from = now.minusSeconds(windowSeconds);
        if (lab.changedAt().isAfter(from)) {
            from = lab.changedAt();
            recent = null;
        }
        Window current = recent == null ? telemetry.window(from, now) : recent;
        Instant baselineEnd = lab.fault() == Fault.HEALTHY || lab.changedAt().isBefore(now.minusSeconds(840))
            ? from : lab.changedAt();
        Instant baselineStart = baselineEnd.minusSeconds(baselineSeconds);
        Instant floor = baselineFloor;
        if (floor.isAfter(baselineStart)) {
            baselineStart = floor.isAfter(baselineEnd) ? baselineEnd : floor;
        }
        return new Assessment(current, telemetry.window(baselineStart, baselineEnd));
    }

    private Severity severity(Assessment assessment) {
        if (!rules.degraded(assessment.baseline(), assessment.current())) {
            return null;
        }
        return assessment.current().total().errorRate() >= 0.5 ? Severity.CRITICAL : Severity.WARNING;
    }

    synchronized TrafficSimulator.State changeFault(Fault next) {
        var before = simulator.state().fault();
        var state = simulator.fault(next);
        if (before != next) {
            if (next == Fault.HEALTHY || before == Fault.DEPENDENCY_FAILURE) {
                baselineFloor = state.changedAt();
            }
            observingIncident = false;
        }
        return state;
    }

    record LiveCheckout(Window metrics, TrafficSimulator.State lab, boolean enoughSamples, Severity severity) {}
    private record Assessment(Window current, Window baseline) {}
}
