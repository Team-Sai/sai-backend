package org.teamsai.saibackend.domain.settlement.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.settlement.type.SplitType;

import java.time.LocalDate;

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

    @NotNull(message = "분배 방식을 선택해 주세요.")
    private SplitType splitType;

    @NotNull(message = "납부 기한을 입력해 주세요.")
    @FutureOrPresent(message = "납부 기한은 오늘 이후여야 합니다.")
    private LocalDate dueDate;
}
