package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("guardian")
@RestController
@RequestMapping("/api/v1")
@OpenAPIDefinition(info = @Info(title = "ReleaseGuard AI", version = "v1"),
    security = @SecurityRequirement(name = "localBasic"))
@SecurityScheme(name = "localBasic", type = SecuritySchemeType.HTTP, scheme = "basic")
class GuardianApi {
    private final Clock clock;
    private final Flagsmith flags;
    private final OllamaInvestigator ollama;
    private final TrafficSimulator simulator;
    private final IncidentMonitor monitor;
    private final ReleaseStore store;
    private final InvestigationService investigations;
    private final RemediationService remediation;
    private final String environment;

    GuardianApi(Clock clock, Flagsmith flags, OllamaInvestigator ollama, TrafficSimulator simulator,
                IncidentMonitor monitor, ReleaseStore store, InvestigationService investigations,
                RemediationService remediation, @Value("${release.flags.label}") String environment) {
        this.clock = clock;
        this.flags = flags;
        this.ollama = ollama;
        this.simulator = simulator;
        this.monitor = monitor;
        this.store = store;
        this.investigations = investigations;
        this.remediation = remediation;
        this.environment = environment;
    }

    @GetMapping("/dashboard")
    Overview dashboard(Principal user) {
        return new Overview(clock.instant(), environment, user.getName(), monitor.recent(), simulator.state(),
            store.incidents(), store.investigations(), store.proposals(), store.auditEvents());
    }

    @GetMapping("/integrations")
    Integrations integrations() {
        try {
            var inventory = flags.inventory();
            var payment = inventory.stream().filter(flag -> PAYMENT.equals(flag.name())).findFirst();
            payment.ifPresent(store::observe);
            return new Integrations(true, "Management API connected", ollama.available(), ollama.label(),
                inventory, store.flagHistory());
        } catch (ServiceFault e) {
            return new Integrations(false, e.getMessage(), ollama.available(), ollama.label(),
                List.of(), store.flagHistory());
        }
    }

    @PostMapping("/lab/traffic")
    TrafficSimulator.State start(@RequestBody @Valid TrafficRequest request) {
        return simulator.start(request.rate(), request.seconds(), request.seed());
    }

    @PostMapping("/lab/traffic/stop")
    TrafficSimulator.State stop() {
        return simulator.stop();
    }

    @PostMapping("/lab/fault")
    TrafficSimulator.State fault(@RequestBody @Valid FaultRequest request) {
        return monitor.changeFault(request.fault());
    }

    @PostMapping("/incidents/{id}/investigation")
    Investigation investigate(@PathVariable UUID id, Principal user) {
        return investigations.run(id, user.getName());
    }

    @PostMapping("/proposals/{id}/decision")
    Proposal decide(@PathVariable UUID id, @RequestBody @Valid ApprovalRequest request, Principal user) {
        return remediation.decide(id, request.decision(), user.getName());
    }

    record TrafficRequest(@Min(1) @Max(20) int rate, @Min(10) @Max(900) int seconds, long seed) {}
    record FaultRequest(@NotNull Fault fault) {}
    record ApprovalRequest(@NotNull Decision decision) {}
    record Overview(Instant at, String environment, String user, Window metrics, TrafficSimulator.State lab,
                    List<Incident> incidents, List<Investigation> investigations, List<Proposal> proposals, List<Audit> audit) {}
    record Integrations(boolean flagsmith, String flagsmithDetail, boolean ai, String provider,
                        List<FlagState> flags, List<FlagObservation> history) {}
}
