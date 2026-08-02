package org.teamsai.saibackend.domain.matching.policy;

import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;

import java.util.List;

@Component
public class AutoMatchingJudge {

    public AutoMatchingResult judge(
            MatchingTransaction transaction,
            List<MatchingCandidate> candidates
    ) {
        validateInput(transaction, candidates);

        if (transaction.transactionType() != AutoMatchingTransactionType.DEPOSIT) {
            return new AutoMatchingResult(List.of());
        }

        List<MatchingCandidate> matchedCandidates = candidates.stream()
                .filter(candidate -> isMatched(transaction, candidate))
                .toList();

        return new AutoMatchingResult(matchedCandidates);
    }

    private boolean isMatched(
            MatchingTransaction transaction,
            MatchingCandidate candidate
    ) {
        return isAmountMatched(transaction, candidate)
                && isParticipantNameMatched(transaction, candidate);
    }

    private boolean isAmountMatched(
            MatchingTransaction transaction,
            MatchingCandidate candidate
    ) {
        // MVP 자동매칭은 입금액과 남은 납부금액이 정확히 같은 경우만 허용한다.
        return transaction.amount()
                .compareTo(candidate.remainingAmount()) == 0;
    }

    private boolean isParticipantNameMatched(
            MatchingTransaction transaction,
            MatchingCandidate candidate
    ) {
        // MVP 이후 실제 은행 연동 시 계좌 ID나 연결 키 기반 식별로 리팩터링한다.
        return normalizeName(transaction.counterpartyName())
                .equals(normalizeName(candidate.participantName()));
    }

    private String normalizeName(String name) {
        return name.replaceAll("\\s+", "");
    }

    private void validateInput(
            MatchingTransaction transaction,
            List<MatchingCandidate> candidates
    ) {
        if (transaction == null || candidates == null) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        if (candidates.stream().anyMatch(candidate -> candidate == null)) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }
}
