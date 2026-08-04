package org.teamsai.saibackend.domain.contract.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLoanContractResponse {

    private Long contractId;

    private Long previousContractId;
    private Long creditorId;
    private Long debtorId;

    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private RepaymentMethod repaymentType;

    private LocalDate startDate;
    private LocalDate maturityDate;

    private Integer repaymentDay;

    private String creditorAddress;
    private String debtorAddress;
    private String contractAlias;
    private String terms;

    private ContractStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
