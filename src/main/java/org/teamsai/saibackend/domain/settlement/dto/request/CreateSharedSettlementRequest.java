package org.teamsai.saibackend.domain.settlement.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSharedSettlementRequest {
    @NotBlank(message = "정산 성격을 입력해 주세요.")
    @Size(max = 50, message = "정산 성격은 50자 이하로 입력해 주세요.")
    private String settlementCategory;

    @NotBlank(message = "정산명을 입력해주세요.")
    @Size(max = 200, message = "정산명은 200자 이하로 입력해 주세요.")
    private String title;

    @NotNull(message = "납부 기한을 입력해 주세요.")
    @FutureOrPresent(message = "납부 기한은 오늘 이후여야 합니다.")
    private LocalDate dueDate;

    @NotNull(message = "총 금액을 입력해 주세요.")
    @DecimalMin(value = "1", message = "총 금액은 1원 이상이어야 합니다.")
    @Digits(integer = 13, fraction = 0, message = "총 금액은 원 단위로 입력해 주세요.")
    private BigDecimal totalAmount;

    @NotNull(message = "정산 수취 계좌를 선택해 주세요.")
    private Long linkedAccountId;


    @Valid
    @NotEmpty(message = "납부자를 한 명 이상 선택해 주세요.")
    private List<CreateSettlementInvitationRequest> invitations;
}
