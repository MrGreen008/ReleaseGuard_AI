package dev.releaseguard;

import static dev.releaseguard.Domain.*;

interface Investigator {
    Finding investigate(Incident incident, InvestigationTools.Evidence evidence);
    String label();
}
