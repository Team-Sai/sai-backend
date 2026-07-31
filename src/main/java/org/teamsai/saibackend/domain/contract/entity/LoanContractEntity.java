package org.teamsai.saibackend.domain.contract.entity;
//DB와 1:1로 대화하는 전용 통로

import lombok.*;
import org.teamsai.saibackend.domain.contract.entity.LoanContractStatus;
import org.teamsai.saibackend.global.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanContractEntity extends BaseEntity {

    private Long contractId;          // (PK)
    private Long previousContractId;  // (이전 계약서 ID)
    private Long creditorId;            // (FK - 채권자 ID)
    private Long debtorId;              // (FK - 채무자 ID)

    private BigDecimal principalAmount; // (대출원금)
    private BigDecimal interestRate;    // (연이자율)
    private String repaymentType;       // (상환방식)
    private LocalDate startDate;        // (대출시작일)
    private LocalDate maturityDate;     // (만기일)
    private Integer repaymentDay;       // (매월 상환일)

    private LoanContractStatus status;  // (계약상태)
    private String address;             // (주소)
    private String contractAlias;       // (계약 별칭)
    private String terms;               // (특약)

    private String creditorSignature;   // (채권자 전자서명)
    private String debtorSignature;     // (채무자 전자서명)

}
