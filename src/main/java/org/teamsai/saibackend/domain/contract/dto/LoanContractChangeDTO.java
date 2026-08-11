package org.teamsai.saibackend.domain.contract.dto;


import lombok.*;

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
    private Integer newRepaymentDate;
    private ChangeRequestStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long contractId;

}
