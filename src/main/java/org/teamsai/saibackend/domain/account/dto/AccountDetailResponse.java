package org.teamsai.saibackend.domain.account.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountDetailResponse(
        Long accountId,
        Long identityId,
        String bankCode,
        String accountNumber,
        String accountName,
        String accountHolderName,
        BigDecimal balance,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}