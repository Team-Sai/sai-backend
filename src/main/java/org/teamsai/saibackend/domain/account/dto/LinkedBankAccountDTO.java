package org.teamsai.saibackend.domain.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedBankAccountDTO {
    private Long linkedAccountId;
    private Long userId;
    private Long accountId;
    private String bankCode;
    private String accountNumber;
    private String accountAlias;
    private Long balance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String accountHolderName;
    private ConnectionStatus connectionStatus;
    private String userKeyHash;
}
