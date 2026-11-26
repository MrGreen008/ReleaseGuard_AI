package dev.releaseguard;

import static dev.releaseguard.Domain.*;
import static dev.releaseguard.EvidenceFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.List;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class OllamaInvestigatorTest {
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final Clock clock = Clock.systemUTC();
    private final JsonHttp transport = mock(JsonHttp.class);
    private final ReleaseStore store = mock(ReleaseStore.class);
    private final Flagsmith flags = mock(Flagsmith.class);
    private final InvestigationTools tools = new InvestigationTools(flags, store, clock);
    private final OllamaInvestigator ollama = new OllamaInvestigator(transport, json, tools,
        new IncidentRules(12, 4, 0.20, 250), "http://unused", "fixture");

    @Test
    void realToolProtocolProducesValidatedOutput() throws Exception {
        var incident = incident(clock.instant());
        var state = enabledFlag();
        when(flags.inventory()).thenReturn(List.of(state,
            new FlagState(20, 25, "inventory_v2", true, "redacted", List.of(), 0, "newer")));
        when(store.observe(state)).thenReturn(new FlagObservation(clock.instant(), state));
        var evidence = tools.evidence(incident);
        when(transport.send(eq("Ollama"), anyString(), eq("POST"), anyMap(), any(), any())).thenReturn(
            response("""
                {"message":{"tool_calls":[{"function":{"name":"compare_cohorts","arguments":{"label":"discarded"}}},
                  {"function":{"name":"get_dependency_health","arguments":null}},
                  {"function":{"name":"get_flag_configuration"}}]}}
                """),
            response(json.writeValueAsString(java.util.Map.of("message", java.util.Map.of("content",
                json.writeValueAsString(investigation(incident, state).finding()))))));
        assertThat(ollama.investigate(incident, evidence).recommendation()).isEqualTo(Recommendation.ROLLBACK);
        assertThat(evidence.traces()).extracting(ToolTrace::name)
            .containsExactly("compare_cohorts", "get_dependency_health", "get_flag_configuration");
        verify(transport).send(eq("Ollama"), anyString(), eq("POST"), anyMap(),
            argThat(body -> body instanceof java.util.Map<?, ?> request && request.containsKey("format")
                && !request.containsKey("tools")), any());
        verify(flags, never()).disable(any());
    }

    @Test
    void unknownToolAndUnreviewedOutputFailClosed() throws Exception {
        var incident = incident(clock.instant());
        when(transport.send(eq("Ollama"), anyString(), eq("POST"), anyMap(), any(), any())).thenReturn(
            response("""
                {"message":{"tool_calls":[{"function":{"name":"approve_rollback","arguments":{}}}]}}
                """),
            response("{\"message\":{\"content\":\"{\\\"recommendation\\\":\\\"ROLLBACK\\\"}\"}}"));
        assertThatThrownBy(() -> ollama.investigate(incident, tools.evidence(incident))).isInstanceOf(ServiceFault.class);
        assertThatThrownBy(() -> ollama.investigate(incident, tools.evidence(incident))).isInstanceOf(ServiceFault.class);
        verifyNoInteractions(flags);
    }

    @Test
    void unavailableProviderDoesNotInventAResult() {
        var incident = incident(clock.instant());
        when(transport.send(eq("Ollama"), anyString(), eq("POST"), anyMap(), any(), any()))
            .thenThrow(ServiceFault.unavailable("Model unavailable."));
        assertThatThrownBy(() -> ollama.investigate(incident, tools.evidence(incident))).hasMessage("Model unavailable.");
        verifyNoInteractions(flags);
    }

    private JsonHttp.Result response(String value) throws Exception {
        return new JsonHttp.Result(200, json.readTree(value));
    }
}
