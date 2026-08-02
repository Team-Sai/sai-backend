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
    public Long createContract(LoanContractRequest request, String userKey) {
        UserDTO currentUser = userMapper.findByUserKey(userKey)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);

        UserDTO debtor = userMapper.findByEmail(request.getDebtorEmail())
                .orElseThrow(LoanContractErrorCode.DEBTOR_NOT_FOUND::toException);

        contractMapper.insertByContract(request, currentUser, debtor);
        return request.getContractId();
    }

    @Transactional
    public ContractStatus submitAndSendToDebtor(Long contractId){
        contractMapper.updateContractStatus(contractId, ContractStatus.PENDING);
        return ContractStatus.PENDING;
    }

    @Transactional
    public ContractStatus approveByDebtor(Long contractId) {
        contractMapper.updateContractStatus(contractId, ContractStatus.COMPLETED);
        return ContractStatus.COMPLETED;
    }

    @Transactional
    public ContractStatus submitCreditorSignature(Long contractId, MultipartFile signature) {
        String signaturePath = fileService.saveSignatureFile(contractId, signature);
        contractMapper.updateCreditorSignature(contractId, signaturePath, ContractStatus.PENDING);
        return ContractStatus.PENDING;
    }

    @Transactional
    public ContractStatus submitDebtorSignature(Long contractId, MultipartFile signature) {
        String signaturePath = fileService.saveSignatureFile(contractId, signature);
        contractMapper.updateDebtorSignature(contractId, signaturePath, ContractStatus.COMPLETED);
        return ContractStatus.COMPLETED;
    }

    public LoanContractResponse findContract(Long contractId) {
        return contractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);
    }


}
