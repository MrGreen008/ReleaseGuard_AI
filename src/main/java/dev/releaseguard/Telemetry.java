package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("guardian")
@Component
class Telemetry {
    private static final int MAX_SAMPLES = 10_000;
    private static final Duration RETENTION = Duration.ofMinutes(15);
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();

    synchronized void add(Sample sample) {
        samples.addLast(sample);
        Instant cutoff = sample.timestamp().minus(RETENTION);
        while (!samples.isEmpty() && (samples.size() > MAX_SAMPLES || samples.getFirst().timestamp().isBefore(cutoff))) {
            samples.removeFirst();
        }
    }

    synchronized Window window(Instant from, Instant to) {
        var included = samples.stream().filter(sample -> !sample.timestamp().isBefore(from)
            && sample.timestamp().isBefore(to)).toList();
        var cohorts = new EnumMap<Cohort, Stats>(Cohort.class);
        for (Cohort cohort : Cohort.values()) {
            cohorts.put(cohort, summarize(included.stream().filter(sample -> sample.cohort() == cohort).toList()));
        }
        return new Window(from, to, summarize(included), Map.copyOf(cohorts));
    }

    private static Stats summarize(List<Sample> samples) {
        var times = samples.stream().mapToLong(Sample::latencyMs).sorted().toArray();
        long failures = samples.stream().filter(sample -> sample.status() >= 500).count();
        long successes = samples.stream().filter(sample -> sample.status() >= 200 && sample.status() < 300).count();
        int count = samples.size();
        return new Stats(count, successes, failures, count == 0 ? 0 : (double) failures / count,
            samples.stream().mapToLong(Sample::latencyMs).average().orElse(0),
            count == 0 ? 0 : times[(int) Math.ceil(0.95 * count) - 1],
            samples.stream().collect(Collectors.groupingBy(Sample::status, Collectors.counting())),
            samples.stream().filter(sample -> !"none".equals(sample.error()))
                .collect(Collectors.groupingBy(Sample::error, Collectors.counting())));
    }
}
