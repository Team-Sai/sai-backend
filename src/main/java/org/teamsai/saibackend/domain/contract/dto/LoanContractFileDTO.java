package org.teamsai.saibackend.domain.contract.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class LoanContractFileDTO {
    //계약서 파일 DTO

    private Long fileId;            // 파일ID (PK)
    private Long contractId;        // 계약서ID (FK, 1:1 관계)

    private String originalFilename; // 원본 파일명 (예: 홍길동_차용증.pdf)
    private String savedFilename;    // UUID 저장 파일명 (예: uuid-abcd-1234.pdf)
    private Long fileSize;           // 파일 크기
    private String fileType;         // 파일 타입 (pdf)
}
