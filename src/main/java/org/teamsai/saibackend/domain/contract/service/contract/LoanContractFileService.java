package org.teamsai.saibackend.domain.contract.service.contract;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.LoanContractFileDTO;
import org.teamsai.saibackend.domain.contract.exception.LoanContractFileErrorCode;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractFileMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class LoanContractFileService {
    private static final Path SIGNATURE_DIR = Path.of("uploads", "signatures");

    private final LoanContractFileMapper fileMapper;

    @Transactional
    public Long insertContractFile(LoanContractFileDTO file) {
        fileMapper.insertContractFile(file);
        return file.getFileId();
    }

    public LoanContractFileDTO findFileByContractId(Long contractId) {
        return fileMapper.findFileByContractId(contractId)
                .orElseThrow(LoanContractFileErrorCode.CONTRACT_FILE_NOT_FOUND::toException);
    }

    
    public String saveSignatureFile(Long contractId, MultipartFile signature) {
        try {
            Files.createDirectories(SIGNATURE_DIR);

            String extension = extractExtension(signature.getOriginalFilename());
            String savedFilename = contractId + "_" + UUID.randomUUID() + extension;
            Path savedPath = SIGNATURE_DIR.resolve(savedFilename);
            Files.copy(signature.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);

            return savedPath.toString();
        } catch (IOException e) {
            log.error("서명 파일 저장 실패 (contractId={})", contractId, e);
            throw LoanContractFileErrorCode.SIGNATURE_UPLOAD_FAILED.toException();
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }

        int lastSeparator = Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\'));
        String filename = originalFilename.substring(lastSeparator + 1);

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == filename.length() - 1) {
            return "";
        }

        String extension = filename.substring(dotIndex + 1);
        return extension.matches("[a-zA-Z0-9]{1,10}") ? "." + extension : "";
    }
}
