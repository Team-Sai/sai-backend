package org.teamsai.saibackend.domain.contract.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.contract.dto.ContractAccountDTO;
import org.teamsai.saibackend.domain.contract.dto.ContractAccountStatus;

import java.util.List;

@Mapper
public interface ContractAccountMapper {

    void insertContractAccount(@Param("account") ContractAccountDTO account);

    int updateContractAccountStatus(
            @Param("contractId") Long contractId,
            @Param("status") ContractAccountStatus status
    );

    List<ContractAccountDTO> findActiveAccountByContractId(@Param("contractId") Long contractId);
}