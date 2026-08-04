package org.teamsai.saibackend.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.exception.BankTransactionErrorCode;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankTransactionService {

    private final BankTransactionMapper bankTransactionMapper;

    @Transactional
    public Long saveIfNotExists(BankTransactionDTO bankTransaction) {
        validateNewBankTransaction(bankTransaction);

        bankTransactionMapper.insertOrGetId(bankTransaction);

        Long bankTransactionId = bankTransaction.getBankTransactionId();
        if (bankTransactionId == null) {
            throw BankTransactionErrorCode
                    .BANK_TRANSACTION_CREATE_FAILED
                    .toException();
        }

        return bankTransactionId;
    }

    public List<BankTransactionDTO> findPendingDeposits() {
        return bankTransactionMapper.findPendingDeposits();
    }

    @Transactional
    public void updateStatus(
            Long bankTransactionId,
            BankTransactionProcessingStatus currentStatus,
            BankTransactionProcessingStatus nextStatus
    ) {
        validateStatusTransition(currentStatus, nextStatus);

        int updatedCount = bankTransactionMapper.updateStatus(
                bankTransactionId,
                currentStatus,
                nextStatus
        );

        if (updatedCount != 1) {
            throw BankTransactionErrorCode
                    .BANK_TRANSACTION_STATUS_UPDATE_FAILED
                    .toException();
        }
    }

    private void validateNewBankTransaction(
            BankTransactionDTO bankTransaction
    ) {
        if (bankTransaction == null
                || hasInvalidRequiredField(bankTransaction)
                || hasInvalidAmount(bankTransaction)
                || hasInvalidInitialStatus(bankTransaction)) {
            throw BankTransactionErrorCode.INVALID_BANK_TRANSACTION
                    .toException();
        }
    }

    private boolean hasInvalidRequiredField(
            BankTransactionDTO bankTransaction
    ) {
        return bankTransaction.getLinkedAccountId() == null
                || isBlank(bankTransaction.getExternalTransactionId())
                || bankTransaction.getTransactionType() == null
                || bankTransaction.getTransactionAt() == null
                || bankTransaction.getSyncedAt() == null;
    }

    private boolean hasInvalidAmount(BankTransactionDTO bankTransaction) {
        return bankTransaction.getAmount() == null
                || bankTransaction.getAmount().signum() <= 0;
    }

    private boolean hasInvalidInitialStatus(
            BankTransactionDTO bankTransaction
    ) {
        return bankTransaction.getProcessingStatus() != null
                && bankTransaction.getProcessingStatus()
                != BankTransactionProcessingStatus.PENDING;
    }

    private void validateStatusTransition(
            BankTransactionProcessingStatus currentStatus,
            BankTransactionProcessingStatus nextStatus
    ) {
        if (currentStatus != BankTransactionProcessingStatus.PENDING
                || !isTerminalStatus(nextStatus)) {
            throw BankTransactionErrorCode
                    .INVALID_BANK_TRANSACTION_STATUS_TRANSITION
                    .toException();
        }
    }

    private boolean isTerminalStatus(
            BankTransactionProcessingStatus processingStatus
    ) {
        return processingStatus == BankTransactionProcessingStatus.APPLIED
                || processingStatus
                == BankTransactionProcessingStatus.UNMATCHED
                || processingStatus
                == BankTransactionProcessingStatus.NEEDS_CHECK
                || processingStatus
                == BankTransactionProcessingStatus.FAILED;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
