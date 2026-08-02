package org.teamsai.saibackend.domain.contract.service.contract;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.LoanContractFileDTO;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
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
    public Long saveFile(LoanContractFileDTO file) {
        fileMapper.insertContractFile(file);
        return file.getFileId();
    }

    public LoanContractFileDTO readFile(Long contractId) {
        return fileMapper.findFileByContractId(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_FILE_NOT_FOUND::toException);
    }

    /**
     * Writes the uploaded signature image to disk under uploads/signatures
     * and returns the saved path. Signatures aren't tracked in
     * loan_contract_file - the path is stored directly on
     * loan_contract.creditor_signature / debtor_signature.
     */
    public String saveSignatureFile(Long contractId, MultipartFile signature) {
        try {
            Files.createDirectories(SIGNATURE_DIR);

            String savedFilename = contractId + "_" + UUID.randomUUID() + "_" + signature.getOriginalFilename();
            Path savedPath = SIGNATURE_DIR.resolve(savedFilename);
            Files.copy(signature.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);

            return savedPath.toString();
        } catch (IOException e) {
            log.error("서명 파일 저장 실패 (contractId={})", contractId, e);
            throw LoanContractErrorCode.SIGNATURE_UPLOAD_FAILED.toException();
        }
    }
}
