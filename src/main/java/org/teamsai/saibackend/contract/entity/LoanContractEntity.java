package org.teamsai.saibackend.contract.entity;
//DB와 1:1로 대화하는 전용 통로

import lombok.*;
import org.teamsai.saibackend.global.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanContractEntity extends BaseEntity {

    // 1. PK 및 FK (DB 컬럼 매핑)
    private Long contractId;          // contract_id (PK)
    private Long previousContractId;  // previous_contract_id (이전 계약서 ID)
    private Long creditorId;            // creditor_id (FK - 채권자 ID)
    private Long debtorId;              // debtor_id (FK - 채무자 ID)

    // 2. 대출 조건
    private BigDecimal principalAmount; // principal_amount (대출원금)
    private BigDecimal interestRate;    // interest_rate (연이자율)
    private String repaymentType;       // repayment_type (상환방식)
    private LocalDate startDate;        // start_date (대출시작일)
    private LocalDate maturityDate;     // maturity_date (만기일)
    private Integer repaymentDay;       // repayment_day (매월 상환일)

    // 3. 계약 상세 내용 및 상태
    private LoanContractStatus status;  // status (계약상태)
    private String address;             // address (주소)
    private String contractAlias;       // contract_alias (계약 별칭)
    private String terms;               // terms (특약)

    // 4. 전자 서명
    private String creditorSignature;   // creditor_signature (채권자 전자서명)
    private String debtorSignature;     // debtor_signature (채무자 전자서명)

    // ※ createdAt, updatedAt은 BaseEntity를 상속(extends)받으므로 별도로 적지 않습니다.
}
