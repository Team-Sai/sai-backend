package org.teamsai.saibackend.domain.contract.service.contract;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contract.dto.LoanContractFileDTO;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractFileMapper;

@Slf4j
@Service
@AllArgsConstructor
public class LoanContractFileService {
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
}
