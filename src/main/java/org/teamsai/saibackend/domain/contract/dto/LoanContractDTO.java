package org.teamsai.saibackend.domain.contract.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 아래는 UserDTO가 아직 없어 user 테이블 join 결과를 담기 위한 임시 컬럼
    private String creditorName;            // 채권자 이름 (user join)
    private LocalDate creditorBirthDate;    // 채권자 생년월일 (user join)
    private String debtorName;              // 채무자 이름 (user join)
    private LocalDate debtorBirthDate;      // 채무자 생년월일 (user join)
}
