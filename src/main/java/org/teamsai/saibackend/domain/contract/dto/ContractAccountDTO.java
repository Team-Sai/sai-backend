package org.teamsai.saibackend.domain.contract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractAccountDTO {

    private Long contractAccountId;
    private Long linkedAccountId;
    private ContractAccountStatus accountStatus;
    private LocalDateTime selectedAt;
    private LocalDateTime endedAt;
    private Long contractId;
}