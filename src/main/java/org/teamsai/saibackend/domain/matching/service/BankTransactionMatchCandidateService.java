package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateDTO;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateQueryDTO;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.mapper.BankTransactionMatchCandidateMapper;
import org.teamsai.saibackend.domain.matching.service.EvaluatedMatchingCandidate;
import org.teamsai.saibackend.domain.matching.type.MatchingCandidateInvalidationReason;
import org.teamsai.saibackend.domain.matching.type.MatchingCandidateStatus;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankTransactionMatchCandidateService {

    private final BankTransactionMatchCandidateMapper candidateMapper;

    @Transactional
    public void saveAll(
            Long bankTransactionId,
            List<EvaluatedMatchingCandidate> evaluatedCandidates
    ) {
        validateSaveRequest(bankTransactionId, evaluatedCandidates);

        if (evaluatedCandidates.isEmpty()) {
            return;
        }

        LocalDateTime createdAt = LocalDateTime.now();

        List<BankTransactionMatchCandidateDTO> candidates =
                evaluatedCandidates.stream()
                        .map(candidate -> toDto(
                                bankTransactionId,
                                candidate,
                                createdAt
                        ))
                        .toList();

        int insertedCount = candidateMapper.insertAll(candidates);

        if (insertedCount != candidates.size()) {
            throw MatchingErrorCode
                    .MATCHING_CANDIDATE_SAVE_FAILED
                    .toException();
        }
    }

    public List<BankTransactionMatchCandidateDTO>
    findAllByBankTransactionId(Long bankTransactionId) {
        validateBankTransactionId(bankTransactionId);

        return candidateMapper.findAllByBankTransactionId(
                bankTransactionId
        );
    }

    public List<BankTransactionMatchCandidateQueryDTO>
    findAllForReviewByBankTransactionId(Long bankTransactionId) {
        validateBankTransactionId(bankTransactionId);

        return candidateMapper.findAllForReviewByBankTransactionId(
                bankTransactionId
        );
    }

    public List<BankTransactionMatchCandidateQueryDTO>
    findAllForReviewByBankTransactionIds(
            List<Long> bankTransactionIds,
            MatchingTargetType targetType,
            Long aggregateId
    ) {
        if (bankTransactionIds == null
                || bankTransactionIds.isEmpty()
                || bankTransactionIds.stream()
                .anyMatch(id -> id == null || id <= 0)) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        return candidateMapper.findAllForReviewByBankTransactionIds(
                bankTransactionIds,
                targetType,
                aggregateId
        );
    }

    public BankTransactionMatchCandidateDTO
    findByIdAndBankTransactionId(
            Long matchCandidateId,
            Long bankTransactionId
    ) {
        validateMatchCandidateId(matchCandidateId);
        validateBankTransactionId(bankTransactionId);

        return candidateMapper.findByIdAndBankTransactionId(
                        matchCandidateId,
                        bankTransactionId
                )
                .orElseThrow(
                        MatchingErrorCode
                                .MATCHING_CANDIDATE_NOT_FOUND
                                ::toException
                );
    }

    @Transactional
    public void invalidateCandidate(
            Long matchCandidateId,
            Long bankTransactionId,
            MatchingCandidateInvalidationReason invalidationReason
    ) {
        validateMatchCandidateId(matchCandidateId);
        validateBankTransactionId(bankTransactionId);

        if (invalidationReason == null) {
            throw MatchingErrorCode
                    .INVALID_MATCHING_REQUEST
                    .toException();
        }

        int updatedCount = candidateMapper.invalidate(
                matchCandidateId,
                bankTransactionId,
                invalidationReason,
                LocalDateTime.now()
        );

        if (updatedCount != 1) {
            throw MatchingErrorCode
                    .MATCHING_CANDIDATE_INVALIDATION_FAILED
                    .toException();
        }
    }

    public int countAvailableCandidates(Long bankTransactionId) {
        validateBankTransactionId(bankTransactionId);

        return candidateMapper.countAvailableByBankTransactionId(
                bankTransactionId
        );
    }

    private BankTransactionMatchCandidateDTO toDto(
            Long bankTransactionId,
            EvaluatedMatchingCandidate evaluatedCandidate,
            LocalDateTime createdAt
    ) {
        return BankTransactionMatchCandidateDTO.builder()
                .bankTransactionId(bankTransactionId)
                .targetType(
                        evaluatedCandidate.candidate().targetType()
                )
                .targetId(
                        evaluatedCandidate.candidate().targetId()
                )
                .expectedRemainingAmount(
                        evaluatedCandidate.candidate().remainingAmount()
                )
                .amountMatchType(
                        evaluatedCandidate.amountMatchType()
                )
                .candidateStatus(MatchingCandidateStatus.AVAILABLE)
                .createdAt(createdAt)
                .build();
    }

    private void validateSaveRequest(
            Long bankTransactionId,
            List<EvaluatedMatchingCandidate> evaluatedCandidates
    ) {
        validateBankTransactionId(bankTransactionId);

        if (evaluatedCandidates == null
                || evaluatedCandidates.stream()
                .anyMatch(candidate -> candidate == null)) {
            throw MatchingErrorCode
                    .INVALID_MATCHING_REQUEST
                    .toException();
        }
    }

    private void validateBankTransactionId(Long bankTransactionId) {
        if (bankTransactionId == null || bankTransactionId <= 0) {
            throw MatchingErrorCode
                    .INVALID_MATCHING_REQUEST
                    .toException();
        }
    }

    private void validateMatchCandidateId(Long matchCandidateId) {
        if (matchCandidateId == null || matchCandidateId <= 0) {
            throw MatchingErrorCode
                    .INVALID_MATCHING_REQUEST
                    .toException();
        }
    }
}
