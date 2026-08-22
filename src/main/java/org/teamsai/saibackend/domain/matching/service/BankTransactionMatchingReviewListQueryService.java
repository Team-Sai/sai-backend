package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateQueryDTO;
import org.teamsai.saibackend.domain.matching.dto.request.MatchingReviewSearchCondition;
import org.teamsai.saibackend.domain.matching.dto.response.BankTransactionMatchCandidateResponse;
import org.teamsai.saibackend.domain.matching.dto.response.BankTransactionMatchingReviewResponse;
import org.teamsai.saibackend.domain.matching.mapper.BankTransactionMatchingReviewQueryMapper;
import org.teamsai.saibackend.domain.matching.type.MatchingReviewChannel;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionDetailResponse;
import org.teamsai.saibackend.domain.transaction.dto.response.PageResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankTransactionMatchingReviewListQueryService {

    private final BankTransactionMatchingReviewQueryMapper reviewQueryMapper;
    private final BankTransactionMatchCandidateService candidateService;

    public PageResponse<BankTransactionMatchingReviewResponse> getReviews(
            Long userId,
            MatchingReviewSearchCondition condition
    ) {
        List<BankTransactionDTO> transactions =
                reviewQueryMapper.search(userId, condition);
        long totalCount = reviewQueryMapper.count(userId, condition);

        if (transactions.isEmpty()) {
            return PageResponse.of(
                    List.of(),
                    condition.page(),
                    condition.size(),
                    totalCount
            );
        }

        List<Long> transactionIds = transactions.stream()
                .map(BankTransactionDTO::getBankTransactionId)
                .toList();

        Map<Long, List<BankTransactionMatchCandidateQueryDTO>> candidatesByTransaction =
                candidateService.findAllForReviewByBankTransactionIds(
                                transactionIds,
                                condition.targetType(),
                                condition.aggregateId()
                        ).stream()
                        .collect(Collectors.groupingBy(
                                BankTransactionMatchCandidateQueryDTO::getBankTransactionId
                        ));

        List<BankTransactionMatchingReviewResponse> content = transactions.stream()
                .map(transaction -> toResponse(
                        transaction,
                        candidatesByTransaction.getOrDefault(
                                transaction.getBankTransactionId(),
                                List.of()
                        )
                ))
                .toList();

        return PageResponse.of(
                content,
                condition.page(),
                condition.size(),
                totalCount
        );
    }

    private BankTransactionMatchingReviewResponse toResponse(
            BankTransactionDTO transaction,
            List<BankTransactionMatchCandidateQueryDTO> candidates
    ) {
        return new BankTransactionMatchingReviewResponse(
                BankTransactionDetailResponse.from(transaction),
                determineReviewChannel(candidates),
                candidates.stream()
                        .map(BankTransactionMatchCandidateResponse::from)
                        .toList()
        );
    }

    private MatchingReviewChannel determineReviewChannel(
            List<BankTransactionMatchCandidateQueryDTO> candidates
    ) {
        return MatchingReviewChannel.TRANSACTION_HISTORY;
    }
}
