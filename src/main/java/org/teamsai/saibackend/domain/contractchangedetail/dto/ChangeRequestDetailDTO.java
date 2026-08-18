package org.teamsai.saibackend.domain.contractchangedetail.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter@Builder
@NoArgsConstructor@AllArgsConstructor
public class ChangeRequestDetailDTO {

    private Long changeRequestId;
    private Long newContractId;
    private String requesterName;
    private LocalDateTime requestedAt;
    private String status;
    private LocalDate currentMaturityDate;
    private BigDecimal currentInterestRate;
    private String currentRepaymentType;
    private String currentTerms;
    private BigDecimal currentMonthlyPayment;
    private LocalDate newMaturityDate;
    private BigDecimal newInterestRate;
    private String newRepaymentType;
    private String newTerms;
    private BigDecimal newMonthlyPayment;
    private String changeReason;
    private Integer extendedMonths;
    private String returnReason;

}