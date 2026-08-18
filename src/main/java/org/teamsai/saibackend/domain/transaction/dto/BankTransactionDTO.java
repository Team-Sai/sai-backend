package org.teamsai.saibackend.domain.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.matching.type.FailureReason;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankTransactionDTO {

    private Long bankTransactionId;

    private Long linkedAccountId;

    private String externalTransactionId;

    private BigDecimal amount;

    private BankTransactionType transactionType;

    private BankTransactionProcessingStatus processingStatus;

    private LocalDateTime transactionAt;

    private String counterpartyName;

    private String memo;

    private LocalDateTime syncedAt;

    private Integer retryCount;
}