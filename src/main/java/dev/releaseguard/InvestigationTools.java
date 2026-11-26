package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("guardian")
@Component
class InvestigationTools {
    private static final Map<String, String> DESCRIPTIONS = Map.of(
        "compare_cohorts", "Compare baseline and incident checkout samples, failures and latency by evaluated cohort.",
        "get_flag_configuration", "Read the current payment flag configuration and other flag names.",
        "get_dependency_health", "Read observed checkout error classes and counts for the two cohorts.",
        "get_flag_history", "Read ReleaseGuard's previous flag observations. These are observation times.",
        "get_previous_remediations", "Read statuses of earlier approved and rejected proposals.");
    private final Flagsmith flags;
    private final ReleaseStore store;
    private final Clock clock;

    InvestigationTools(Flagsmith flags, ReleaseStore store, Clock clock) {
        this.flags = flags;
        this.store = store;
        this.clock = clock;
    }

    List<Map<String, Object>> schemas() {
        return DESCRIPTIONS.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry ->
            Map.<String, Object>of("type", "function", "function", Map.of(
                "name", entry.getKey(), "description", entry.getValue(), "parameters",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)))).toList();
    }

    Evidence evidence(Incident incident) {
        return new Evidence(incident);
    }

    final class Evidence {
        private final Incident incident;
        private final Map<String, ToolTrace> traces = new LinkedHashMap<>();
        private FlagObservation flag;

        private Evidence(Incident incident) {
            this.incident = incident;
        }

        Object read(String tool) {
            if (!DESCRIPTIONS.containsKey(tool) || traces.containsKey(tool)) {
                throw ServiceFault.unavailable("AI requested an unavailable or repeated tool.");
            }
            Object result = switch (tool) {
                case "compare_cohorts" -> Map.of("endpoint", CHECKOUT, "baseline", incident.baseline(),
                    "incident", incident.current());
                case "get_flag_configuration" -> flagConfiguration();
                case "get_dependency_health" -> Map.of("counts", incident.current().total().errors(),
                    "stable", incident.current().cohorts().get(Cohort.STABLE).errors(),
                    "experimental", incident.current().cohorts().get(Cohort.EXPERIMENTAL).errors());
                case "get_flag_history" -> store.flagHistory().stream()
                    .map(item -> Map.of("observedAt", item.at(), "enabled", item.state().enabled())).toList();
                case "get_previous_remediations" -> store.proposals().stream().limit(5)
                    .map(proposal -> Map.of("status", proposal.status(), "createdAt", proposal.createdAt())).toList();
                default -> throw ServiceFault.unavailable("AI requested an unavailable tool.");
            };
            traces.put(tool, new ToolTrace(tool, clock.instant()));
            return result;
        }

        private Object flagConfiguration() {
            List<FlagState> inventory = flags.inventory();
            var payment = inventory.stream().filter(candidate -> PAYMENT.equals(candidate.name())).toList();
            if (payment.size() != 1) {
                throw ServiceFault.unavailable("Flagsmith payment configuration was unavailable.");
            }
            flag = store.observe(payment.get(0));
            return Map.of("payment", flag, "otherFlags", inventory.stream()
                .filter(candidate -> !PAYMENT.equals(candidate.name())).map(FlagState::name).toList());
        }

        boolean has(String tool) {
            return traces.containsKey(tool);
        }

        List<ToolTrace> traces() {
            return List.copyOf(traces.values());
        }

        FlagObservation flag() {
            return flag;
        }
    }
}
