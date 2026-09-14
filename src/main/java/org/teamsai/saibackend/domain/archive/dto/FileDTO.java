package org.teamsai.saibackend.domain.archive.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDTO {

    private Long fileId;
    private ArchiveStatus domainType;      // 도메인 구분 (예: CONTRACT, SETTLEMENT)
    private Long referenceId;

    private String originalFilename;
    private String savedFilename;
    private Long fileSize;
    private String fileType;

    private LocalDateTime createdAt;
}