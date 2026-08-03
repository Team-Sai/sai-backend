package org.teamsai.saibackend.domain.contract.service.contract;


import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractRequest;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractMapper;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;

@Slf4j
@Service
@AllArgsConstructor
public class LoanContractService {
    private final LoanContractMapper contractMapper;
    private final LoanContractFileService fileService;
    private final UserMapper userMapper;

    @Transactional
    public Long createContract(LoanContractRequest request, Long userId) {
        UserDTO currentUser = userMapper.findById(userId)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);

        UserDTO debtor = userMapper.findByEmail(request.getDebtorEmail())
                .orElseThrow(LoanContractErrorCode.DEBTOR_NOT_FOUND::toException);

        contractMapper.insertByContract(request, currentUser, debtor);
        return request.getContractId();
    }

    @Transactional
    public ContractStatus submitCreditorSignature(Long contractId, Long userId, MultipartFile signature) {
        LoanContractResponse contract = contractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);

        if (!contract.getCreditorId().equals(userId)) {
            throw LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException();
        }

        String savedPath = fileService.saveSignatureFile(contractId, signature);
        contractMapper.updateCreditorSignature(contractId, savedPath, ContractStatus.PENDING);

        return ContractStatus.PENDING;
    }


    @Transactional
    public ContractStatus submitDebtorSignature(Long contractId, Long userId, String debtorAddress, MultipartFile signature) {

        LoanContractResponse contract = contractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);

        if (!contract.getDebtorId().equals(userId)) {
            throw LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException();
        }

        String savedPath = fileService.saveSignatureFile(contractId, signature);
        contractMapper.updateDebtorSignature(contractId, debtorAddress, savedPath, ContractStatus.COMPLETED);

        return ContractStatus.COMPLETED;
    }

    public LoanContractResponse findContract(Long contractId, Long userId) {
        LoanContractResponse contract = contractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);

        boolean isParty = contract.getCreditorId().equals(userId) || contract.getDebtorId().equals(userId);
        if (!isParty) {
            throw LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException();
        }

        return contract;
    }


}
