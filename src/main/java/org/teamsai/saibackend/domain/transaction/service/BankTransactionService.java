package org.teamsai.saibackend.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
        return bankTransactionMapper.findByExternalKey(
                        bankTransaction.getLinkedAccountId(),
                        bankTransaction.getExternalTransactionId()
                )
                .map(BankTransactionDTO::getBankTransactionId)
                .orElseGet(() -> insertOrFindExisting(bankTransaction));
    }

    public List<BankTransactionDTO> findPendingDeposits() {
        return bankTransactionMapper.findPendingDeposits();
    }

    @Transactional
    public void updateStatus(
            Long bankTransactionId,
            BankTransactionProcessingStatus processingStatus
    ) {
        int updatedCount = bankTransactionMapper.updateStatus(
                bankTransactionId,
                processingStatus
        );

        if (updatedCount != 1) {
            throw BankTransactionErrorCode
                    .BANK_TRANSACTION_STATUS_UPDATE_FAILED
                    .toException();
        }
    }

    private Long insertOrFindExisting(BankTransactionDTO bankTransaction) {
        try {
            int insertedCount = bankTransactionMapper.insert(bankTransaction);

            if (insertedCount != 1
                    || bankTransaction.getBankTransactionId() == null) {
                throw BankTransactionErrorCode.BANK_TRANSACTION_CREATE_FAILED
                        .toException();
            }

            return bankTransaction.getBankTransactionId();

        } catch (DuplicateKeyException exception) {
            return bankTransactionMapper.findByExternalKey(
                            bankTransaction.getLinkedAccountId(),
                            bankTransaction.getExternalTransactionId()
                    )
                    .map(BankTransactionDTO::getBankTransactionId)
                    .orElseThrow(
                            BankTransactionErrorCode
                                    .BANK_TRANSACTION_CREATE_FAILED::toException
                    );
        }
    }
}
