package org.teamsai.saibackend.domain.contract.entity;

import lombok.*;
import org.teamsai.saibackend.domain.contract.entity.ChangeRequestStatus;
import org.teamsai.saibackend.global.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanContractChangeRequestEntity extends BaseEntity {

    private Long changeRequestId;       // (PK)
    private Long userId;                // (FK - 요청한 회원 ID)
    private Long contractId;            // (FK - 대상 계약서 ID)

    private String changeReason;        // change_reason (변경 사유)

    private LocalDate newMaturityDate;  // new_maturity_date (변경 만기일)
    private BigDecimal newInterestRate; // new_interest_rate (변경 이자율)
    private String newRepaymentType;    // new_repayment_type (변경 상환방식)
    private LocalDate newRepaymentDate; // new_repayment_date (변경 상환일)

    private ChangeRequestStatus status;  // status (요청 상태: PENDING, APPROVED)


}
