package org.teamsai.saibackend.contract.entity;

import org.teamsai.saibackend.global.common.BaseEntity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanContractFileEntity extends BaseEntity {

    private Long fileId;            // file_id (PK)
    private Long contractId;        // contract_id (FK, 1:1 관계)

    private String originalFilename; // original_filename (원본 파일명)
    private String savedFilename;    // saved_filename (UUID 저장 파일명)
    private Long fileSize;           // file_size (파일 크기, Bytes)
    private String fileType;         // file_type (파일 타입, 예: application/pdf)

    // ※ createdAt, updatedAt은 BaseEntity를 상속받아 자동으로 관리됩니다.
}
