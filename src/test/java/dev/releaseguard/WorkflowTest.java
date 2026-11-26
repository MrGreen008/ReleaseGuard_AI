package dev.releaseguard;

import static dev.releaseguard.Domain.*;
import static dev.releaseguard.EvidenceFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "release.auth.viewer=workflow-viewer-password",
    "release.auth.approver=workflow-approver-password",
    "release.flags.management-environment=isolated",
    "release.flags.environment-key=isolated",
    "release.flags.allowed=isolated"
})
@ActiveProfiles("guardian")
@AutoConfigureMockMvc
class WorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ReleaseStore store;
    @Autowired Clock clock;
    @MockitoBean Flagsmith flags;

    @Test
    void approvalDispatchesExactWriteOnceAndPersistsReadback() throws Exception {
        var proposal = proposal(180);
        when(flags.payment()).thenReturn(enabledFlag(), disabledFlag());
        decide(proposal.id(), "APPROVE", "APPROVER", true)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFYING"));
        decide(proposal.id(), "APPROVE", "APPROVER", true).andExpect(status().isConflict());
        verify(flags, times(1)).disable(enabledFlag());
        assertThat(store.auditEvents()).anyMatch(event -> event.reference().equals(proposal.id())
            && event.event().equals("VERIFYING"));
        store.advance(Status.VERIFYING, store.proposal(proposal.id()).withStatus(Status.INCONCLUSIVE,
            "Test closed the observation window."), "test");
    }

    @Test
    void expiredRejectedAndUnauthorizedDecisionsPerformNoWrites() throws Exception {
        var rejected = proposal(180);
        decide(rejected.id(), "APPROVE", "VIEWER", true).andExpect(status().isForbidden());
        decide(rejected.id(), "APPROVE", "APPROVER", false).andExpect(status().isForbidden());
        decide(rejected.id(), "REJECT", "APPROVER", true)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        var expired = proposal(-1);
        decide(expired.id(), "APPROVE", "APPROVER", true)
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(200, 409));
        assertThat(store.proposal(expired.id()).status()).isEqualTo(Status.EXPIRED);
        verify(flags, never()).disable(any());
    }

    @Test
    void mismatchedRevisionOrManagementFailurePreventsWrite() throws Exception {
        var stale = proposal(180);
        when(flags.payment()).thenReturn(disabledFlag());
        decide(stale.id(), "APPROVE", "APPROVER", true)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("STALE"));
        var offline = proposal(180);
        when(flags.payment()).thenThrow(ServiceFault.unavailable("Flagsmith unavailable."));
        decide(offline.id(), "APPROVE", "APPROVER", true)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        verify(flags, never()).disable(any());
    }

    @Test
    void approvalRequiresAuthenticationAndAValidDecision() throws Exception {
        mvc.perform(post("/api/v1/proposals/" + UUID.randomUUID() + "/decision")
            .header("X-ReleaseGuard-Intent", "1")).andExpect(status().isUnauthorized());
        decide(UUID.randomUUID(), "RUN", "APPROVER", true).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentClaimsHaveOneWinner() {
        var proposal = proposal(180);
        var first = CompletableFuture.supplyAsync(() -> claim(proposal.id()));
        var second = CompletableFuture.supplyAsync(() -> claim(proposal.id()));
        assertThat(java.util.List.of(first.join(), second.join())).containsExactlyInAnyOrder(true, false);
        store.advance(Status.EXECUTING, store.proposal(proposal.id()).withStatus(Status.FAILED,
            "Test ended before dispatch."), "test");
    }

    private boolean claim(UUID id) {
        try {
            return store.decide(id, Decision.APPROVE, "approver").status() == Status.EXECUTING;
        } catch (ServiceFault e) {
            assertThat(e.status().value()).isEqualTo(409);
            return false;
        }
    }

    private org.springframework.test.web.servlet.ResultActions decide(UUID id, String choice, String role,
            boolean explicit) throws Exception {
        var request = post("/api/v1/proposals/" + id + "/decision")
            .with(user(role.toLowerCase()).roles(role)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"" + choice + "\"}");
        if (explicit) {
            request.header("X-ReleaseGuard-Intent", "1");
        }
        return mvc.perform(request);
    }

    private Proposal proposal(long ttl) {
        var at = clock.instant();
        var incident = incident(at);
        var proposal = new Proposal(UUID.randomUUID(), incident.id(), "releaseguard-lab", enabledFlag(), false,
            at, at.plusSeconds(ttl), "approver", null, null, Status.PENDING_APPROVAL, "Review", null);
        store.recordIncident(incident);
        store.recordInvestigation(investigation(incident, enabledFlag()), proposal, "approver");
        return proposal;
    }
}
