package org.teamsai.saibackend.domain.contractchange.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter@Builder
@NoArgsConstructor@AllArgsConstructor
public class ContractChangeRequest {

    private String changeReason;
    private LocalDate newMaturityDate;
    private BigDecimal newInterestRate;
    private String newRepaymentType;
    private LocalDate newRepaymentDate;
    private Long userId;

}
