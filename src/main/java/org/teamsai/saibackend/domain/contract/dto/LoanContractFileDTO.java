package org.teamsai.saibackend.domain.contract.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class LoanContractFileDTO {


    private Long fileId;            // PK
    private Long contractId;        // FK

    private String originalFilename;
    private String savedFilename;
    private Long fileSize;
    private String fileType;

    private LocalDateTime createdAt;
}
