package org.teamsai.saibackend.domain.archive.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.archive.dto.FileDTO;
import org.teamsai.saibackend.domain.archive.mapper.ArchiveMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.teamsai.saibackend.domain.archive.dto.ArchiveStatus.CONTRACT;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveService {

    private final ArchiveMapper archiveMapper;

    @Getter
    @Value("${file.upload-dir:C:/upload/shinhan/}")
    private String uploadDir = "C:/upload/shinhan/";

    public List<FileDTO> findFilesByReference(String domainType, Long referenceId) {
        return archiveMapper.findFilesByReference(domainType, referenceId);
    }

    public List<FileDTO> findAllFilesByUserId(Long userId) {
        return archiveMapper.findAllFilesByUserId(userId);
    }

    @Transactional
    public FileDTO saveFile(String domainType, Long referenceId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 존재하지 않습니다.");
        }

        try {

            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            }

            String savedFilename = domainType + "_" + referenceId + "_" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

            Path savePath = dirPath.resolve(savedFilename);
            Files.copy(file.getInputStream(), savePath, StandardCopyOption.REPLACE_EXISTING);

            FileDTO fileDTO = FileDTO.builder()
                    .domainType(CONTRACT)
                    .referenceId(referenceId)
                    .originalFilename(originalFilename)
                    .savedFilename(savedFilename)
                    .fileSize(file.getSize())
                    .fileType(file.getContentType())
                    .createdAt(LocalDateTime.now())
                    .build();

            archiveMapper.insertFile(fileDTO);

            log.info("[File Saved] Domain: {}, RefId: {}, Original: {} -> Saved: {}",
                    domainType, referenceId, originalFilename, savedFilename);

            return fileDTO;

        } catch (IOException e) {
            log.error("파일 저장 중 오류 발생", e);
            throw new RuntimeException("파일 저장 처리 중 오류가 발생했습니다.", e);
        }
    }

    public FileDTO getFileById(Long fileId) {
        return archiveMapper.findFileById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 파일입니다. fileId=" + fileId));
    }


    public Resource loadFileAsResource(String savedFilename) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(savedFilename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                log.error("파일을 찾을 수 없거나 읽을 수 없습니다: {}", filePath);
                throw new RuntimeException("파일을 찾을 수 없거나 읽을 수 없습니다.");
            }
        } catch (MalformedURLException e) {
            log.error("파일 경로 해석 오류: {}", savedFilename, e);
            throw new RuntimeException("파일 경로 해석 중 오류가 발생했습니다.", e);
        }
    }
}
