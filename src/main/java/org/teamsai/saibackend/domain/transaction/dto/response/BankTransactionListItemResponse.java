package org.teamsai.saibackend.domain.transaction.dto.response;

import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankTransactionListItemResponse(
        Long bankTransactionId,
        BigDecimal amount,
        BankTransactionType transactionType,
        BankTransactionProcessingStatus processingStatus,
        LocalDateTime transactionAt,
        String counterpartyName,
        String memo
) {
    public static BankTransactionListItemResponse from(BankTransactionDTO dto) {
        return new BankTransactionListItemResponse(
                dto.getBankTransactionId(),
                dto.getAmount(),
                dto.getTransactionType(),
                dto.getProcessingStatus(),
                dto.getTransactionAt(),
                dto.getCounterpartyName(),
                dto.getMemo()
        );
    }
}