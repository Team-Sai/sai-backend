package org.teamsai.saibackend.domain.contractchange.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractReadDTO;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractSummaryResponse {

    private Long contractId;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private LocalDate maturityDate;
    private String repaymentType;
    private Integer repaymentDay;

    public static ContractSummaryResponse from(LoanContractReadDTO contract) {
        return ContractSummaryResponse.builder()
                .contractId(contract.getContractId())
                .principalAmount(contract.getPrincipalAmount())
                .interestRate(contract.getInterestRate())
                .maturityDate(contract.getMaturityDate())
                .repaymentType(contract.getRepaymentType())
                .repaymentDay(contract.getRepaymentDay())
                .build();
    }


}
