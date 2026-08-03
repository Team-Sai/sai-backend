package org.teamsai.saibackend.domain.contract.service.contract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.LoanContractFileDTO;
import org.teamsai.saibackend.domain.contract.exception.LoanContractFileErrorCode;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractFileMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanContractFileService {

    private final LoanContractFileMapper fileMapper;

    @Getter
    @Value("${file.upload-dir:C:/upload/shinhan/}")
    private String uploadDir = "C:/upload/shinhan/";

    @Transactional
    public Long insertContractFile(LoanContractFileDTO file) {
        fileMapper.insertContractFile(file);
        return file.getFileId();
    }

    public LoanContractFileDTO findFileByContractId(Long contractId) {
        return fileMapper.findFileByContractId(contractId)
                .orElseThrow(LoanContractFileErrorCode.CONTRACT_FILE_NOT_FOUND::toException);
    }

    public String saveSignatureFile(Long contractId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("서명 파일이 존재하지 않습니다.");
        }

        try {
            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String originalFilename = file.getOriginalFilename();
            String ext = "png";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            }

            String savedFilename = contractId + "_" + UUID.randomUUID() + "." + ext;

            Path savePath = dirPath.resolve(savedFilename);
            Files.copy(file.getInputStream(), savePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("[Signature Saved] ContractId: {}, Original: {} -> Saved: {}",
                    contractId, originalFilename, savedFilename);

            return savedFilename;

        } catch (IOException e) {
            log.error("서명 파일 저장 중 오류 발생", e);
            throw LoanContractFileErrorCode.SIGNATURE_UPLOAD_FAILED.toException();
        }
    }
}
