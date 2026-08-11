package org.teamsai.saibackend.domain.transaction.dto.response;

public record TransactionSyncAllResponse(
        int syncedAccountCount,
        int totalTransactionCount,
        int appliedCount,
        int needsCheckCount,
        int unmatchedCount,
        int duplicateCount,
        int failedCount
) {
}
