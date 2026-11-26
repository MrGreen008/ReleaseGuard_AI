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

    Window recent() {
        Instant now = clock.instant();
        return telemetry.window(now.minusSeconds(windowSeconds), now);
    }

    @Scheduled(fixedDelay = 2000)
    synchronized void detect() {
        try {
            Instant now = clock.instant();
            var lab = simulator.state();
            Instant from = now.minusSeconds(windowSeconds);
            if (lab.changedAt().isAfter(from)) {
                from = lab.changedAt();
            }
            var current = telemetry.window(from, now);
            Instant baselineEnd = lab.fault() == Fault.HEALTHY || lab.changedAt().isBefore(now.minusSeconds(840))
                ? from : lab.changedAt();
            var baseline = telemetry.window(baselineEnd.minusSeconds(baselineSeconds), baselineEnd);
            if (current.total().requests() < rules.minSamples) {
                return;
            }
            if (!rules.degraded(baseline, current)) {
                observingIncident = false;
                return;
            }
            if (observingIncident || store.incidents().stream().anyMatch(
                incident -> incident.detectedAt().isAfter(now.minusSeconds(8)))) {
                return;
            }
            var severity = current.total().errorRate() >= 0.5 ? Severity.CRITICAL : Severity.WARNING;
            store.recordIncident(new Incident(UUID.randomUUID(), now, severity, baseline, current));
            observingIncident = true;
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
        }
    }

    synchronized TrafficSimulator.State changeFault(Fault next) {
        var before = simulator.state().fault();
        var state = simulator.fault(next);
        if (before != next) {
            observingIncident = false;
        }
        return state;
    }
}
