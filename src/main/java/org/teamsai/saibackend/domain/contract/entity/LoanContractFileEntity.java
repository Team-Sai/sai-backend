package org.teamsai.saibackend.domain.contract.entity;

import org.teamsai.saibackend.global.common.BaseEntity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanContractFileEntity extends BaseEntity {

    private Long fileId;            // PK
    private Long contractId;        //FK

    private String originalFilename; // (원본 파일명)
    private String savedFilename;    // (UUID 저장 파일명)
    private Long fileSize;           // (파일 크기, Bytes)
    private String fileType;         // (파일 타입, 예: application/pdf)

}
