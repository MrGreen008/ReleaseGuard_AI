package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Profile("guardian")
@Component
class TrafficSimulator {
    private static final Logger log = LoggerFactory.getLogger(TrafficSimulator.class);
    private static final Set<String> ERRORS = Set.of("none", "flag_evaluation_unavailable", "shared_dependency_unavailable",
        "payment_processor_failure", "checkout_interrupted");
    private final ScheduledExecutorService worker = Executors.newScheduledThreadPool(4);
    private final AtomicReference<Run> active = new AtomicReference<>();
    private final Clock clock;
    private final Telemetry telemetry;
    private final JsonHttp http;
    private final String shopUrl;
    private final boolean enabled;
    private final Map<String, String> headers;
    private volatile Fault scenario = Fault.HEALTHY;
    private volatile Instant changedAt;

    TrafficSimulator(Clock clock, Telemetry telemetry, JsonHttp http,
            @Value("${release.shop-url}") String shopUrl,
            @Value("${release.lab-enabled}") boolean enabled,
            @Value("${release.auth.shop}") String shopPassword) {
        this.clock = clock;
        this.telemetry = telemetry;
        this.http = http;
        this.shopUrl = shopUrl;
        this.enabled = enabled;
        this.headers = Map.of("Authorization", LocalSecurity.authHeader("simulator", shopPassword),
            "X-ReleaseGuard-Intent", "1");
        this.changedAt = clock.instant();
    }

    synchronized State start(int rate, int seconds, long seed) {
        requireLab();
        if (state().running()) {
            throw ServiceFault.conflict("Traffic is already running.");
        }
        var run = new Run(clock.instant().plusSeconds(seconds), rate, seed);
        active.set(run);
        for (int i = 0; i < 4; i++) {
            schedule(run, i, 0);
        }
        return state();
    }

    synchronized State stop() {
        requireLab();
        active.set(null);
        return state();
    }

    synchronized State fault(Fault requested) {
        requireLab();
        if (scenario != requested) {
            http.send("Demo Shop", shopUrl + "/api/lab/fault", "POST", headers, requested,
                Duration.ofSeconds(5)).requireOk("Demo Shop");
            scenario = requested;
            changedAt = clock.instant();
        }
        return state();
    }

    State state() {
        var run = active.get();
        return new State(enabled, run != null && clock.instant().isBefore(run.until()), scenario, changedAt,
            run == null ? null : run.until(), run == null ? 0 : run.rate());
    }

    private void schedule(Run run, long sequence, long delayMs) {
        worker.schedule(() -> {
            if (active.get() != run || !clock.instant().isBefore(run.until())) {
                return;
            }
            try {
                sample(sequence, run.seed());
            } catch (RuntimeException e) {
                log.error(e.getMessage(), e);
            } finally {
                if (!worker.isShutdown() && active.get() == run) {
                    schedule(run, sequence + 4, 4_000L / run.rate());
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void sample(long sequence, long seed) {
        String identity = "synthetic-" + Math.floorMod(sequence + seed, 32);
        UUID requestId = UUID.randomUUID();
        long nanos = System.nanoTime();
        Instant started = clock.instant();
        Cohort cohort = Cohort.UNKNOWN;
        int status = 503;
        String error = "shop_unavailable";
        try {
            var outcome = http.send("Demo Shop checkout", shopUrl + CHECKOUT, "POST", headers,
                new DemoShop.CheckoutRequest(requestId, identity, new DemoShop.Cart("mug", 1)), Duration.ofSeconds(7));
            status = outcome.status();
            if (!requestId.toString().equals(outcome.body().path("requestId").asText())) {
                throw ServiceFault.unavailable("Demo Shop returned an unrecognized checkout response.");
            }
            cohort = Cohort.valueOf(outcome.body().path("cohort").asText());
            error = outcome.body().path("error").asText();
            if (!ERRORS.contains(error)) {
                error = "unrecognized_checkout_error";
            }
        } catch (ServiceFault | IllegalArgumentException e) {
            log.error(e.getMessage(), e);
            status = 503;
        }
        telemetry.add(new Sample(requestId, identity, CHECKOUT, started, cohort, status,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanos), error));
    }

    void requireLab() {
        if (!enabled) {
            throw new ServiceFault(HttpStatus.NOT_FOUND, "Incident Lab is disabled.");
        }
    }

    @PreDestroy
    void close() {
        active.set(null);
        worker.shutdownNow();
    }

    record State(boolean enabled, boolean running, Fault fault, Instant changedAt, Instant until, int rate) {}
    private record Run(Instant until, int rate, long seed) {}
}
