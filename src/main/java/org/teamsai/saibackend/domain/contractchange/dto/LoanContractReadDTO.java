package org.teamsai.saibackend.domain.contractchange.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Getter@NoArgsConstructor
@Builder@AllArgsConstructor
public class LoanContractReadDTO {

    private Long contractId;
    private Long previousContractId;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private String repaymentType;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private Integer repaymentDay;
    private String status;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime  updatedAt;
    private String creditorSignature;
    private String debtorSignature;
    private Long creditorId;
    private Long debtorId;
    private String contractAlias;
    private String terms;
}
