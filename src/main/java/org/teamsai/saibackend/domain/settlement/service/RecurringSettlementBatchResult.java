package org.teamsai.saibackend.domain.settlement.service;

import java.util.List;

public record RecurringSettlementBatchResult(
        int totalCandidates,
        int succeeded,
        int failed,
        List<Long> failedRecurringIds
) {
}