package org.teamsai.saibackend.domain.contract.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class LoanContractDTO {

    private Long contractId;          // (PK)
    private Long previousContractId;

    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private String repaymentType;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate maturityDate;
    private Integer repaymentDay;

    private ContractStatus status;
    private String creditorAddress;
    private String debtorAddress;
    private String contractAlias;
    private String terms;

    private Long creditorId;            // (FK)
    private Long debtorId;              // (FK)

    private String creditorSignature;
    private String debtorSignature;

    private Long selectedLinkedAccountId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
