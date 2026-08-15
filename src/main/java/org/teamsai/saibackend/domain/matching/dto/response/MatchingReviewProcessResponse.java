package org.teamsai.saibackend.domain.matching.dto.response;

import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;

public record MatchingReviewProcessResponse(
        Long bankTransactionId,
        BankTransactionProcessingStatus processingStatus
) {
}
