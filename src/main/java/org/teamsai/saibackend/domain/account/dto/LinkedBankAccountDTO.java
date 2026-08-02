package org.teamsai.saibackend.domain.account;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class LinkedBankAccountDTO {
    private Long linkedAccountId;
    private Long userId;
    private String bankCode;
    private String maskedAccountNumber;
    private String accountAlias;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String accountHolderName;
    private ConnectionStatus connectionStatus;
}
