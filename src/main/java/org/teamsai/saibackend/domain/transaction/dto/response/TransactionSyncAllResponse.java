package org.teamsai.saibackend.domain.transaction.dto.response;

import java.util.List;

public record TransactionSyncAllResponse(
        int syncedAccountCount,
        List<AccountSyncFailureResponse> failedAccounts,
        int totalTransactionCount,
        int appliedCount,
        int needsCheckCount,
        int unmatchedCount,
        int duplicateCount,
        int failedCount
) {
}
