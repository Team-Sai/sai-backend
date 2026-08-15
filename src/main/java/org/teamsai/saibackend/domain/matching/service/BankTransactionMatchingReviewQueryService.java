package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateQueryDTO;
import org.teamsai.saibackend.domain.matching.dto.response.BankTransactionMatchCandidateResponse;
import org.teamsai.saibackend.domain.matching.dto.response.BankTransactionMatchingReviewResponse;
import org.teamsai.saibackend.domain.matching.type.MatchingReviewChannel;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionDetailResponse;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionQueryService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankTransactionMatchingReviewQueryService {

    private final BankTransactionQueryService bankTransactionQueryService;
    private final BankTransactionMatchCandidateService candidateService;

    public BankTransactionMatchingReviewResponse getReview(
            Long userId,
            Long linkedAccountId,
            Long bankTransactionId
    ) {
        BankTransactionDetailResponse transaction =
                bankTransactionQueryService.getTransactionDetail(
                        userId,
                        linkedAccountId,
                        bankTransactionId
                );

        List<BankTransactionMatchCandidateQueryDTO> candidates =
                candidateService.findAllForReviewByBankTransactionId(
                        bankTransactionId
                );

        return new BankTransactionMatchingReviewResponse(
                transaction,
                determineReviewChannel(candidates),
                candidates.stream()
                        .map(BankTransactionMatchCandidateResponse::from)
                        .toList()
        );
    }

    private MatchingReviewChannel determineReviewChannel(
            List<BankTransactionMatchCandidateQueryDTO> candidates
    ) {
        boolean hasSettlement = candidates.stream()
                .anyMatch(candidate -> candidate.getTargetType()
                        == MatchingTargetType.SETTLEMENT);

        boolean hasLoan = candidates.stream()
                .anyMatch(candidate -> candidate.getTargetType()
                        == MatchingTargetType.LOAN);

        if (hasSettlement && hasLoan) {
            return MatchingReviewChannel.NOTIFICATION;
        }

        return MatchingReviewChannel.TRANSACTION_HISTORY;
    }
}
