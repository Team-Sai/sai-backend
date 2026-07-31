package org.teamsai.saibackend.domain.contract.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.contract.entity.LoanContractEntity;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanContractService {

    final LoanContractMapper contractRepo;

    public int insertService(LoanContractEntity contract){
        return contractRepo.insertContract(contract);
    }

}
