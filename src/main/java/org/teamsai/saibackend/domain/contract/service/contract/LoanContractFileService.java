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
import java.util.Base64;

@Slf4j
@Service
@AllArgsConstructor
public class LoanContractFileService {

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
            String contentType = signature.getContentType() != null
                    ? signature.getContentType()
                    : "application/octet-stream";
            String base64 = Base64.getEncoder().encodeToString(signature.getBytes());

            return "data:" + contentType + ";base64," + base64;
        } catch (IOException e) {
            log.error("서명 파일 인코딩 실패 (contractId={})", contractId, e);
            throw LoanContractFileErrorCode.SIGNATURE_UPLOAD_FAILED.toException();
        }
    }
}
