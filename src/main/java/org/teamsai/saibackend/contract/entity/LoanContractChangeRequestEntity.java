package org.teamsai.saibackend.contract.entity;

import lombok.*;
import org.teamsai.saibackend.global.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanContractChangeRequestEntity extends BaseEntity {

    // 1. PK 및 FK (DB 컬럼 매핑)
    private Long changeRequestId;       // change_request_id (PK)
    private Long userId;                // user_id (FK - 요청한 회원 ID)
    private Long contractId;            // contract_id (FK - 대상 계약서 ID)

    // 2. 변경 내용 입력 정보
    private String changeReason;        // change_reason (변경 사유)

    private LocalDate newMaturityDate;  // new_maturity_date (변경 만기일)
    private BigDecimal newInterestRate; // new_interest_rate (변경 이자율)
    private String newRepaymentType;    // new_repayment_type (변경 상환방식)
    private LocalDate newRepaymentDate; // new_repayment_date (변경 상환일)

    // 3. 상태
    private ChangeRequestStatus status;  // status (요청 상태: PENDING, APPROVED)

    // ※ createdAt(요청 일시), updatedAt(처리 일시)은 BaseEntity를 상속받아 자동으로 관리됩니다.
}
