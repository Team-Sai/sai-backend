package org.teamsai.saibackend.domain.contract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.teamsai.saibackend.contract.entity.ChangeRequestStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class LoanContractChangeRequestDTO {

    private Long changeRequestId;         // 변경요청 ID (PK)
    private Long userId;                  // 요청한 회원 ID (FK)
    private Long contractId;              // 대상 계약서 ID (FK)

    private String changeReason;          // 변경 사유

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate newMaturityDate;    // 변경 만기일

    private BigDecimal newInterestRate;   // 변경 이자율

    private String newRepaymentType;      // 변경 상환방식

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate newRepaymentDate;   // 변경 상환일

    private ChangeRequestStatus status;   // 요청 상태 (PENDING, APPROVED)
    private LocalDateTime createdAt;      // 요청 일시
    private LocalDateTime updatedAt;    // 처리 일시

    // MVP 이후에 거절 기능 추가?
}
