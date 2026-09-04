package org.teamsai.saibackend.domain.contract.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import org.teamsai.saibackend.domain.contract.type.ContractRole;
import org.teamsai.saibackend.domain.contract.type.DashboardContractStatus;
import org.teamsai.saibackend.domain.contract.type.DashboardPaymentStatus;
import org.teamsai.saibackend.domain.contract.type.TransactionCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class DashboardContractRowResponse {

    private Long contractId;
    private String contractAlias;
    private ContractRole role;
    private TransactionCategory category;
    private BigDecimal principalAmount;
    private BigDecimal totalRemainingAmount;
    private BigDecimal thisMonthDueAmount;
    private DashboardContractStatus contractStatus;
    private String repaymentStatus;
    private DashboardPaymentStatus paymentStatus;
    private LocalDate maturityDate;
    private LocalDate nearestScheduleDueDate;
    private BigDecimal nextDueAmount;

    @JsonIgnore
    private LocalDateTime createdAt;
}
