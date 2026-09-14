package org.teamsai.saibackend.domain.transaction.dto.response;

import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankTransactionDetailResponse(
        Long bankTransactionId,
        Long linkedAccountId,
        BigDecimal amount,
        BankTransactionType transactionType,
        BankTransactionProcessingStatus processingStatus,
        LocalDateTime transactionAt,
        String counterpartyName,
        String memo,
        LocalDateTime syncedAt
) {
    public static BankTransactionDetailResponse from(BankTransactionDTO dto) {
        return new BankTransactionDetailResponse(
                dto.getBankTransactionId(),
                dto.getLinkedAccountId(),
                dto.getAmount(),
                dto.getTransactionType(),
                dto.getProcessingStatus(),
                dto.getTransactionAt(),
                dto.getCounterpartyName(),
                dto.getMemo(),
                dto.getSyncedAt()
        );
    }
}