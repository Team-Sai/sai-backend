package org.teamsai.saibackend.domain.matching.model;

public record AutoMatchingExecutionResult(
        int totalTransactionCount,
        int appliedCount,
        int needsCheckCount,
        int unmatchedCount,
        int duplicateCount,
        int failedCount
) {
}
