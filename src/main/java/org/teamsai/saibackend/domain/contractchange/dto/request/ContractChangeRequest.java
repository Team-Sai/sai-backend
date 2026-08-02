package org.teamsai.saibackend.domain.contractchange.dto.request;

import jakarta.validation.constraints.*;
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
    @DecimalMax(value = "20", message = "이율은 20% 이하여야 합니다.")
    @Digits(integer = 2, fraction = 2, message = "이율은 소수점 둘째 자리까지만 입력 가능합니다.")
    private BigDecimal newInterestRate;

    @NotBlank(message = "상환 방식은 필수입니다.")
    private String newRepaymentType;

    @NotNull(message = "변경 상환일은 필수입니다.")
    @Future(message = "변경 상환일은 오늘 이후 날짜여야 합니다.")
    private LocalDate newRepaymentDate;



}
