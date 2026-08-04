package org.teamsai.saibackend.domain.contract.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanContractRequest {

    @NotBlank(message = "본인인증 식별값은 필수입니다.")
    private String identityVerificationId;

    private Long contractId;

    @NotNull(message = "대출원금을 입력해주세요.")
    @Positive(message = "대출원금은 0보다 커야 합니다.")
    private BigDecimal principalAmount;

    @NotNull(message = "연이자율을 입력해주세요.")
    @DecimalMin(value = "0.0", inclusive = false, message = "연이자율은 0보다 커야 합니다.")
    @DecimalMax(value = "20.0", inclusive = true, message = "연이자율은 20%를 초과할 수 없습니다.")
    private BigDecimal interestRate;

    @NotNull(message = "상환방식을 클릭해주세요.")
    private RepaymentMethod repaymentType;

    @NotNull(message = "대출 시작일을 입력해주세요.")
    private LocalDate startDate;

    @NotNull(message = "대출 만기일을 입력해주세요.")
    private LocalDate maturityDate;

    @NotNull(message = "상환일을 입력해주세요.")
    @Min(value = 1, message = "상환일은 1일 이상이어야 합니다.")
    @Max(value = 31, message = "상환일은 31일 이하이어야 합니다.")
    private Integer repaymentDay;

    @NotBlank(message = "주소를 입력해주세요.")
    private String creditorAddress;

    @NotBlank(message = "채무자 이메일을 입력해주세요.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String debtorEmail;

    @NotBlank(message = "계약의 목적을 입력해주세요.")
    private String contractAlias;

    private String terms;

    @AssertTrue(message = "대출 만기일은 시작일 이후여야 합니다.")
    public boolean isValidMaturityDate() {
        if (startDate == null || maturityDate == null) return true;
        return maturityDate.isAfter(startDate);
    }
}