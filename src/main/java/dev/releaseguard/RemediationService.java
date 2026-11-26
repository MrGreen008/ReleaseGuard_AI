package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Profile("guardian")
@Service
class RemediationService {
    private static final Logger log = LoggerFactory.getLogger(RemediationService.class);
    private final ReleaseStore store;
    private final Flagsmith flags;
    private final Telemetry telemetry;
    private final IncidentRules rules;
    private final Clock clock;
    private final boolean environmentAllowed;
    private final String environment;
    private final long recoverySeconds;

    RemediationService(ReleaseStore store, Flagsmith flags, Telemetry telemetry, IncidentRules rules, Clock clock,
            @Value("${release.flags.label}") String environment,
            @Value("${release.flags.management-environment}") String target,
            @Value("${release.flags.environment-key}") String sdkTarget,
            @Value("${release.flags.allowed}") String allowed,
            @Value("${release.analysis.recovery-seconds}") long recoverySeconds) {
        if (recoverySeconds < 3 || recoverySeconds > 300) {
            throw new IllegalArgumentException("Recovery duration is out of range.");
        }
        this.store = store;
        this.flags = flags;
        this.telemetry = telemetry;
        this.rules = rules;
        this.clock = clock;
        this.environment = environment;
        this.environmentAllowed = !target.isBlank() && target.equals(allowed) && target.equals(sdkTarget);
        this.recoverySeconds = recoverySeconds;
    }

    synchronized Proposal decide(UUID id, Decision decision, String actor) {
        if (!"approver".equals(actor)) {
            throw new ServiceFault(HttpStatus.FORBIDDEN, "An approver is required.");
        }
        Proposal proposal = store.decide(id, decision, actor);
        if (proposal.status() != Status.EXECUTING) {
            return proposal;
        }
        if (!environmentAllowed || !environment.equals(proposal.environment()) || proposal.desiredEnabled()
            || !PAYMENT.equals(proposal.expected().name()) || rules.rollbackBlock(
                store.incident(proposal.incidentId()), proposal.expected()) != null) {
            return executionOutcome(proposal, Status.FAILED, "Proposal is outside the deterministic mutation policy.");
        }
        FlagState current;
        try {
            current = flags.payment();
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            return executionOutcome(proposal, Status.FAILED, "Flagsmith state check failed before the write.");
        }
        if (!current.equals(proposal.expected()) || !clock.instant().isBefore(proposal.expiresAt())) {
            return executionOutcome(proposal, Status.STALE, "Expected flag configuration or approval deadline changed.");
        }
        try {
            flags.disable(proposal.expected());
            FlagState result = flags.payment();
            if (result.stateId() != current.stateId() || result.enabled() || result.overrides() != 0
                || !current.variants().equals(result.variants()) || !current.value().equals(result.value())) {
                return executionOutcome(proposal, Status.UNCERTAIN, "Readback did not match the approved flag configuration.");
            }
            store.observe(result);
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            return executionOutcome(proposal, Status.UNCERTAIN, "Mutation or readback failed. Reconcile in Flagsmith.");
        }
        return store.advance(Status.EXECUTING, proposal.transition(Status.VERIFYING, null, clock.instant(),
            "Flagsmith readback verified. Collecting fresh checkout traffic.", null), "executor");
    }

    @Scheduled(fixedDelay = 2000)
    synchronized void progress() {
        try {
            Instant now = clock.instant();
            for (Proposal pending : store.proposals(Status.PENDING_APPROVAL)) {
                if (!now.isBefore(pending.expiresAt())) {
                    store.advance(Status.PENDING_APPROVAL, pending.withStatus(Status.EXPIRED, "Approval deadline elapsed."), "scheduler");
                }
            }
            for (Proposal running : store.proposals(Status.EXECUTING)) {
                if (now.isAfter(running.expiresAt().plusSeconds(45))) {
                    executionOutcome(running, Status.UNCERTAIN, "Execution was interrupted. Reconcile in Flagsmith.");
                }
            }
            for (Proposal verifying : store.proposals(Status.VERIFYING)) {
                Instant end = verifying.verifiedAt().plusSeconds(recoverySeconds);
                if (!now.isBefore(end.plusSeconds(2))) {
                    verify(verifying, end);
                }
            }
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void verify(Proposal proposal, Instant end) {
        try {
            FlagState actual = flags.payment();
            if (actual.enabled() || actual.overrides() != 0 || actual.stateId() != proposal.expected().stateId()
                || !actual.variants().equals(proposal.expected().variants()) || !actual.value().equals(proposal.expected().value())) {
                store.advance(Status.VERIFYING, proposal.withStatus(Status.FAILED,
                    "Flagsmith was changed again during verification."), "verifier");
                return;
            }
        } catch (ServiceFault e) {
            log.error(e.getMessage(), e);
            store.advance(Status.VERIFYING, proposal.withStatus(Status.FAILED,
                "Could not recheck Flagsmith during verification."), "verifier");
            return;
        }
        var fresh = telemetry.window(proposal.verifiedAt(), end);
        var result = rules.recovery(store.incident(proposal.incidentId()), fresh);
        String detail = switch (result) {
            case RECOVERED -> "Fresh stable checkout traffic returned to the healthy range.";
            case PARTIALLY_RECOVERED -> "Fresh traffic improved but remains outside the healthy range. Investigate other causes.";
            case NOT_RECOVERED -> "Fresh traffic remains degraded. Continue dependency investigation.";
            default -> "Fresh stable checkout evidence was insufficient. Observe before drawing a conclusion.";
        };
        store.advance(Status.VERIFYING, proposal.transition(result, null, null, detail, fresh), "verifier");
    }

    private Proposal executionOutcome(Proposal proposal, Status status, String detail) {
        return store.advance(Status.EXECUTING, proposal.withStatus(status, detail), "executor");
    }
}
