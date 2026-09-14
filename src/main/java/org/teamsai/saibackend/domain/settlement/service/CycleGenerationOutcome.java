package org.teamsai.saibackend.domain.settlement.service;

import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;

public record CycleGenerationOutcome(CycleGenerationResult result, SettlementDTO settlement) {

    public static CycleGenerationOutcome created(SettlementDTO settlement) {
        return new CycleGenerationOutcome(CycleGenerationResult.CREATED, settlement);
    }

    public static CycleGenerationOutcome concurrentlySkipped() {
        return new CycleGenerationOutcome(CycleGenerationResult.CONCURRENTLY_SKIPPED, null);
    }

    public static CycleGenerationOutcome noActiveParticipant() {
        return new CycleGenerationOutcome(CycleGenerationResult.NO_ACTIVE_PARTICIPANT, null);
    }
}