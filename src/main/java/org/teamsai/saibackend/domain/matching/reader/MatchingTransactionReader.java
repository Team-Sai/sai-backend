package org.teamsai.saibackend.domain.matching.reader;

import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;

import java.util.List;

public interface MatchingTransactionReader {
    List<MatchingTransaction> readPendingTransactions();
}
