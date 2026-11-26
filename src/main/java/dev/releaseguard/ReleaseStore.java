package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Profile("guardian")
@Repository
class ReleaseStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    ReleaseStore(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public void recordIncident(Incident incident) {
        jdbc.update("INSERT INTO incident (id, detected_at, payload) VALUES (?, ?, ?)",
            incident.id(), timestamp(incident.detectedAt()), encode(incident));
        audit("monitor", "INCIDENT_DETECTED", incident.id(), incident.severity().name() + " checkout degradation");
    }

    Incident incident(UUID id) {
        return required(jdbc.query("SELECT payload FROM incident WHERE id = ?",
            (rs, i) -> decode(rs.getString(1), Incident.class), id), "Incident not found.");
    }

    List<Incident> incidents() {
        return jdbc.query("SELECT payload FROM incident ORDER BY detected_at DESC LIMIT 40",
            (rs, i) -> decode(rs.getString(1), Incident.class));
    }

    List<Investigation> investigations() {
        return jdbc.query("""
            SELECT i.payload FROM investigation i JOIN incident e ON i.incident_id = e.id
            ORDER BY e.detected_at DESC LIMIT 40
            """, (rs, i) -> decode(rs.getString(1), Investigation.class));
    }

    Optional<Investigation> investigation(UUID incidentId) {
        return jdbc.query("SELECT payload FROM investigation WHERE incident_id = ?",
            (rs, i) -> decode(rs.getString(1), Investigation.class), incidentId).stream().findFirst();
    }

    @Transactional
    public void recordInvestigation(Investigation investigation, Proposal proposal, String actor) {
        lock();
        if (investigation(investigation.incidentId()).isPresent()) {
            throw ServiceFault.conflict("This incident already has an investigation.");
        }
        jdbc.update("INSERT INTO investigation (incident_id, payload) VALUES (?, ?)",
            investigation.incidentId(), encode(investigation));
        audit(actor, "INVESTIGATION_COMPLETED", investigation.incidentId(), investigation.policy());
        if (proposal != null) {
            jdbc.update("INSERT INTO proposal (id, incident_id, status, created_at, payload) VALUES (?, ?, ?, ?, ?)",
                proposal.id(), proposal.incidentId(), proposal.status().name(), timestamp(proposal.createdAt()), encode(proposal));
            audit(actor, "APPROVAL_REQUESTED", proposal.id(), "Review exact payment rollback.");
        }
    }

    Proposal proposal(UUID id) {
        return required(jdbc.query("SELECT payload FROM proposal WHERE id = ?",
            (rs, i) -> decode(rs.getString(1), Proposal.class), id), "Proposal not found.");
    }

    List<Proposal> proposals() {
        return jdbc.query("SELECT payload FROM proposal ORDER BY created_at DESC LIMIT 40",
            (rs, i) -> decode(rs.getString(1), Proposal.class));
    }

    List<Proposal> proposals(Status status) {
        return jdbc.query("SELECT payload FROM proposal WHERE status = ? ORDER BY created_at LIMIT 40",
            (rs, i) -> decode(rs.getString(1), Proposal.class), status.name());
    }

    @Transactional
    public Proposal decide(UUID id, Decision decision, String actor) {
        lock();
        Proposal existing = proposal(id);
        if (existing.status() != Status.PENDING_APPROVAL) {
            throw ServiceFault.conflict("Proposal has already been decided or expired.");
        }
        if (!clock.instant().isBefore(existing.expiresAt())) {
            return update(existing.withStatus(Status.EXPIRED, "Approval deadline elapsed."), actor);
        }
        if (decision == Decision.REJECT) {
            return update(existing.transition(Status.REJECTED, actor, null, "Human declined the rollback.", null), actor);
        }
        if (!proposals(Status.EXECUTING).isEmpty() || !proposals(Status.VERIFYING).isEmpty()
            || !proposals(Status.UNCERTAIN).isEmpty()) {
            throw ServiceFault.conflict("An earlier rollback requires completion or operator reconciliation.");
        }
        return update(existing.transition(Status.EXECUTING, actor, null, "Human approved the exact rollback.", null), actor);
    }

    @Transactional
    public Proposal advance(Status previous, Proposal updated, String actor) {
        lock();
        if (proposal(updated.id()).status() != previous) {
            throw ServiceFault.conflict("Proposal changed before this transition.");
        }
        boolean valid = switch (previous) {
            case PENDING_APPROVAL -> updated.status() == Status.EXPIRED;
            case EXECUTING -> List.of(Status.STALE, Status.FAILED, Status.UNCERTAIN, Status.VERIFYING).contains(updated.status());
            case VERIFYING -> List.of(Status.RECOVERED, Status.PARTIALLY_RECOVERED, Status.NOT_RECOVERED,
                Status.INCONCLUSIVE, Status.FAILED).contains(updated.status());
            default -> false;
        };
        if (!valid) {
            throw ServiceFault.conflict("Invalid proposal transition.");
        }
        return update(updated, actor);
    }

    List<Audit> auditEvents() {
        return jdbc.query("""
            SELECT id, recorded_at, actor, event_type, reference_id, detail FROM audit_event
            ORDER BY id DESC LIMIT 100
            """, (rs, i) -> new Audit(rs.getLong(1), rs.getObject(2, OffsetDateTime.class).toInstant(),
                rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6)));
    }

    void audit(String actor, String event, UUID id, String detail) {
        jdbc.update("INSERT INTO audit_event (recorded_at, actor, event_type, reference_id, detail) VALUES (?, ?, ?, ?, ?)",
            timestamp(clock.instant()), actor, event, id, detail);
    }

    @Transactional
    public FlagObservation observe(FlagState flag) {
        lock();
        var previous = jdbc.query("SELECT payload FROM flag_observation WHERE revision = ?",
            (rs, i) -> decode(rs.getString(1), FlagObservation.class), flag.revision());
        if (!previous.isEmpty()) {
            return previous.get(0);
        }
        var observation = new FlagObservation(clock.instant(), flag);
        jdbc.update("INSERT INTO flag_observation (observed_at, revision, payload) VALUES (?, ?, ?)",
            timestamp(observation.at()), flag.revision(), encode(observation));
        return observation;
    }

    List<FlagObservation> flagHistory() {
        return jdbc.query("SELECT payload FROM flag_observation ORDER BY observed_at DESC LIMIT 12",
            (rs, i) -> decode(rs.getString(1), FlagObservation.class));
    }

    private Proposal update(Proposal proposal, String actor) {
        jdbc.update("UPDATE proposal SET status = ?, payload = ? WHERE id = ?",
            proposal.status().name(), encode(proposal), proposal.id());
        audit(actor, proposal.status().name(), proposal.id(), proposal.detail());
        return proposal;
    }

    private void lock() {
        jdbc.queryForObject("SELECT id FROM execution_lock WHERE id = 1 FOR UPDATE", Integer.class);
    }

    private static <T> T required(List<T> rows, String message) {
        return rows.stream().findFirst().orElseThrow(() -> new ServiceFault(HttpStatus.NOT_FOUND, message));
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize workflow record.", e);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not read workflow record.", e);
        }
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
