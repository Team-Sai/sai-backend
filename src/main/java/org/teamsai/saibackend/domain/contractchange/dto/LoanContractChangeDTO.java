package org.teamsai.saibackend.domain.contractchange.dto;


import lombok.*;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Getter@Builder@NoArgsConstructor
@AllArgsConstructor
public class LoanContractChangeDTO {

    private Long changeRequestId;
    private Long userId;
    private String changeReason;
    private LocalDate newMaturityDate;
    private BigDecimal newInterestRate;
    private String newRepaymentType;
    private LocalDate newRepaymentDate;
    private ContractStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long contractId;

}
