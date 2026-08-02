package org.teamsai.saibackend.domain.contractchange.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter@Builder
@NoArgsConstructor@AllArgsConstructor
public class ContractChangeRequest {


    @NotBlank(message = "변경 사유는 필수입니다.")
    private String changeReason;

    @NotNull(message = "변경 만기일은 필수입니다.")
    @Future(message = "변경 만기일은 오늘 이후 날짜여야 합니다.")
    private LocalDate newMaturityDate;

    @NotNull(message = "변경 이율은 필수입니다.")
    @DecimalMin(value = "0", message = "이율은 0 이상이어야 합니다.")
    private BigDecimal newInterestRate;

    @NotBlank(message = "상환 방식은 필수입니다.")
    private String newRepaymentType;

    @NotNull(message = "변경 상환일은 필수입니다.")
    @Future(message = "변경 상환일은 오늘 이후 날짜여야 합니다.")
    private LocalDate newRepaymentDate;



}
