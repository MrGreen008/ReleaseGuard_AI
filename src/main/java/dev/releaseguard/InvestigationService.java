package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("guardian")
@Service
class InvestigationService {
    private final Semaphore capacity = new Semaphore(1);
    private final ReleaseStore store;
    private final InvestigationTools tools;
    private final Investigator investigator;
    private final IncidentRules rules;
    private final Clock clock;
    private final boolean targetAllowed;
    private final String environment;

    InvestigationService(ReleaseStore store, InvestigationTools tools, Investigator investigator,
                IncidentRules rules, Clock clock, @Value("${release.flags.label}") String environment,
                @Value("${release.flags.management-environment}") String target,
                @Value("${release.flags.environment-key}") String sdkTarget,
                @Value("${release.flags.allowed}") String allowed) {
        this.store = store;
        this.tools = tools;
        this.investigator = investigator;
        this.rules = rules;
        this.clock = clock;
        this.environment = environment;
        this.targetAllowed = !target.isBlank() && target.equals(allowed) && target.equals(sdkTarget);
    }

    Investigation run(UUID incidentId, String actor) {
        if (!capacity.tryAcquire()) {
            throw ServiceFault.conflict("Another investigation is already running.");
        }
        try {
            return store.investigation(incidentId).orElseGet(() -> create(incidentId, actor));
        } catch (ServiceFault e) {
            if (e.status().is5xxServerError()) {
                store.audit(actor, "INVESTIGATION_FAILED", incidentId, e.getMessage());
            }
            throw e;
        } finally {
            capacity.release();
        }
    }

    private Investigation create(UUID incidentId, String actor) {
        Incident incident = store.incident(incidentId);
        var evidence = tools.evidence(incident);
        Finding finding = investigator.investigate(incident, evidence);
        var flag = evidence.flag();
        var now = clock.instant();
        var policy = "Read-only recommendation.";
        Proposal proposal = null;
        if (finding.recommendation() == Recommendation.ROLLBACK) {
            String reason = targetAllowed ? rules.rollbackBlock(incident, flag == null ? null : flag.state())
                : "The management and SDK targets must match the allowlisted environment.";
            if (reason == null && incident.detectedAt().plusSeconds(600).isBefore(now)) {
                reason = "The incident evidence is older than 10 minutes.";
            }
            if (reason == null) {
                policy = "Deterministic policy accepted the proposal for human review.";
                proposal = new Proposal(UUID.randomUUID(), incidentId, environment, flag.state(), false,
                    now, now.plusSeconds(180), actor, null, null, Status.PENDING_APPROVAL,
                    "Disable the environment default so checkout uses the stable payment engine.", null);
            } else {
                policy = reason;
            }
        }
        var result = new Investigation(incidentId, now, investigator.label(), finding, evidence.traces(), flag, policy);
        store.recordInvestigation(result, proposal, actor);
        return result;
    }
}
