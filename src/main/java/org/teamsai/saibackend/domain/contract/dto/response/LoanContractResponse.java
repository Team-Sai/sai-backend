package org.teamsai.saibackend.domain.contract.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class LoanContractResponse {

    private Long contractId;

    @JsonIgnore
    private Long creditorId;
    @JsonIgnore
    private Long debtorId;

    private String creditorName;
    private String creditorBirthDate;
    private String creditorAddress;
    private String creditorSignature;

    private String debtorName;
    private String debtorBirthDate;
    private String debtorAddress;
    private String debtorSignature;

    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private RepaymentMethod repaymentType;

    private LocalDate startDate;
    private LocalDate maturityDate;

    private Integer repaymentDay;

    private String contractAlias;
    private String terms;

    private ContractStatus status;
    private LocalDateTime createdAt;
}