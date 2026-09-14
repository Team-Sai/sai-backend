package org.teamsai.saibackend.domain.matching.service;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;

import java.util.List;

public record AutoMatchingExecutionResult(
        int totalTransactionCount,
        int appliedCount,
        int needsCheckCount,
        int unmatchedCount,
        int duplicateCount,
        int failedCount,
        List<AutoMatchingTransactionResult> transactionResults
) {

    public AutoMatchingExecutionResult {
        if (transactionResults == null
                || transactionResults.stream().anyMatch(result -> result == null)
                || totalTransactionCount < 0
                || appliedCount < 0
                || needsCheckCount < 0
                || unmatchedCount < 0
                || duplicateCount < 0
                || failedCount < 0) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        int resultCount = transactionResults.size();

        int countedTotal = appliedCount
                + needsCheckCount
                + unmatchedCount
                + duplicateCount
                + failedCount;

        if (totalTransactionCount != resultCount
                || totalTransactionCount != countedTotal) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        transactionResults = List.copyOf(transactionResults);
    }
}