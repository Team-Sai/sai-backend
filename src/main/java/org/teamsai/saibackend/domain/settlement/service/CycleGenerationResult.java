package org.teamsai.saibackend.domain.settlement.service;

public enum CycleGenerationResult {
    CREATED,
    CONCURRENTLY_SKIPPED,
    NO_ACTIVE_PARTICIPANT
}