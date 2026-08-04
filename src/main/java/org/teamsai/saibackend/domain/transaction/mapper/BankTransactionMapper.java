package org.teamsai.saibackend.domain.transaction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;

import java.util.List;
import java.util.Optional;

@Mapper
public interface BankTransactionMapper {

    int insertOrGetId(BankTransactionDTO bankTransaction);

    Optional<BankTransactionDTO> findById(
            @Param("bankTransactionId") Long bankTransactionId
    );

    Optional<BankTransactionDTO> findByExternalKey(
            @Param("linkedAccountId") Long linkedAccountId,
            @Param("externalTransactionId") String externalTransactionId
    );

    List<BankTransactionDTO> findPendingDeposits();

    int updateStatus(
            @Param("bankTransactionId") Long bankTransactionId,
            @Param("currentStatus")
            BankTransactionProcessingStatus currentStatus,
            @Param("nextStatus")
            BankTransactionProcessingStatus nextStatus
    );
}
