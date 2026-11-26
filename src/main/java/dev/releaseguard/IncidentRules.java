package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("guardian")
@Component
class IncidentRules {
    final int minSamples;
    final int minCohort;
    final double errorThreshold;
    final long latencyThreshold;

    IncidentRules(@Value("${release.analysis.min-samples}") int minSamples,
                  @Value("${release.analysis.min-cohort}") int minCohort,
                  @Value("${release.analysis.error-threshold}") double errorThreshold,
                  @Value("${release.analysis.latency-threshold-ms}") long latencyThreshold) {
        if (minSamples < 2 || minCohort < 2 || errorThreshold <= 0 || errorThreshold >= 1 || latencyThreshold < 1) {
            throw new IllegalArgumentException("Analysis thresholds are out of range.");
        }
        this.minSamples = minSamples;
        this.minCohort = minCohort;
        this.errorThreshold = errorThreshold;
        this.latencyThreshold = latencyThreshold;
    }

    boolean degraded(Window baseline, Window current) {
        if (current.total().requests() < minSamples) {
            return false;
        }
        Stats stable = current.cohorts().get(Cohort.STABLE);
        Stats experiment = current.cohorts().get(Cohort.EXPERIMENTAL);
        boolean failedCohort = experiment.requests() >= minCohort && experiment.errorRate() >= errorThreshold
            || stable.requests() >= minCohort && stable.errorRate() >= errorThreshold;
        return failedCohort || current.total().errorRate() >= errorThreshold
            || baseline.total().requests() >= minSamples && current.total().p95Ms() >= latencyThreshold
                && current.total().p95Ms() - baseline.total().p95Ms() >= latencyThreshold / 2;
    }

    String rollbackBlock(Incident incident, FlagState flag) {
        if (flag == null || !flag.enabled() || flag.overrides() != 0 || !PAYMENT.equals(flag.name())
            || !"stable".equals(flag.value()) || flag.variants().size() != 1 || flag.variants().stream().noneMatch(v ->
                "experimental".equals(v.name()) && v.weight() > 0 && v.weight() < 100)) {
            return "Payment flag must have a supported active rollout without overrides.";
        }
        var baseline = incident.baseline();
        var current = incident.current();
        Stats stable = current.cohorts().get(Cohort.STABLE);
        Stats experiment = current.cohorts().get(Cohort.EXPERIMENTAL);
        if (baseline.total().requests() < minSamples || current.total().requests() < minSamples
            || stable.requests() < minCohort || experiment.requests() < minCohort) {
            return "More baseline and cohort samples are required.";
        }
        if (baseline.total().errorRate() > 0.05 || stable.errorRate() > 0.05
            || stable.p95Ms() >= latencyThreshold || current.cohorts().get(Cohort.UNKNOWN).requests() > 0
            || current.total().errors().containsKey("shared_dependency_unavailable")) {
            return "Baseline, control, or dependency evidence does not support a flag-only change.";
        }
        if (experiment.errorRate() - stable.errorRate() < errorThreshold
            && experiment.p95Ms() - stable.p95Ms() < latencyThreshold) {
            return "Experimental degradation is below the rollback threshold.";
        }
        return null;
    }

    Status recovery(Incident incident, Window fresh) {
        Stats current = fresh.total();
        if (current.requests() < minSamples || fresh.cohorts().get(Cohort.UNKNOWN).requests() > 0
            || fresh.cohorts().get(Cohort.EXPERIMENTAL).requests() > 0) {
            return Status.INCONCLUSIVE;
        }
        Stats healthy = incident.baseline().total();
        Stats degraded = incident.current().total();
        if (current.errorRate() <= Math.max(0.05, healthy.errorRate() + 0.02)
            && current.p95Ms() <= Math.max(latencyThreshold, healthy.p95Ms() + latencyThreshold / 2)) {
            return Status.RECOVERED;
        }
        if (current.errorRate() <= degraded.errorRate() + 0.02
            && current.p95Ms() <= degraded.p95Ms() + latencyThreshold / 4
            && (degraded.errorRate() - current.errorRate() >= 0.10
                || degraded.p95Ms() - current.p95Ms() >= latencyThreshold / 2)) {
            return Status.PARTIALLY_RECOVERED;
        }
        return Status.NOT_RECOVERED;
    }
}
