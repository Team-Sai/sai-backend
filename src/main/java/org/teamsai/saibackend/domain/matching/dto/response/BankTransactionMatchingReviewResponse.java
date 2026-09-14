package org.teamsai.saibackend.domain.matching.dto.response;

import org.teamsai.saibackend.domain.matching.type.MatchingReviewChannel;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionDetailResponse;

import java.util.List;

public record BankTransactionMatchingReviewResponse(
        BankTransactionDetailResponse transaction,
        MatchingReviewChannel reviewChannel,
        List<BankTransactionMatchCandidateResponse> candidates
) {
}
