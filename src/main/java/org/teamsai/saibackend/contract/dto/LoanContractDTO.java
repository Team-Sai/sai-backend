package org.teamsai.saibackend.contract.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanContractDTO {
    //계약서 DTO

    private Long contractId;          // 계약서ID (PK)
    private Long previousContractId;  // 이전계약서ID

    private BigDecimal principalAmount; // 대출원금
    private BigDecimal interestRate;    // 연이자율
    private String repaymentType;       // 상환방식
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;        // 대출시작일
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate maturityDate;     // 만기일
    private Integer repaymentDay;       // 매월 상환일

    private String status;              // 계약상태("PENDING", "SAVED", "COMPLETED")
    private String address;             // 주소
    private String contractAlias;       // 계약 별칭
    private String terms;               // 특약

    private Long creditorId;            // 채권자 ID (FK)
    private Long debtorId;              // 채무자 ID (FK)

    private String creditorSignature;   // 전자서명(채권자)
    private String debtorSignature;     // 전자서명(채무자)
}
