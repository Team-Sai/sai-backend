package org.teamsai.saibackend.domain.matching.model;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;

public record AutoMatchingTransactionResult(
        Long transactionId,
        AutoMatchingProcessStatus processStatus
) {

    public AutoMatchingTransactionResult {
        if (transactionId == null || processStatus == null) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }
}