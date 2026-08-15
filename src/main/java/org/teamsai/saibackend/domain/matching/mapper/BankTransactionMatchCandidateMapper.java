package org.teamsai.saibackend.domain.matching.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateDTO;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateQueryDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface BankTransactionMatchCandidateMapper {

    int insertAll(
            @Param("candidates")
            List<BankTransactionMatchCandidateDTO> candidates
    );

    List<BankTransactionMatchCandidateDTO> findAllByBankTransactionId(
            @Param("bankTransactionId") Long bankTransactionId
    );

    List<BankTransactionMatchCandidateQueryDTO>
    findAllForReviewByBankTransactionId(
            @Param("bankTransactionId") Long bankTransactionId
    );

    Optional<BankTransactionMatchCandidateDTO>
    findByIdAndBankTransactionId(
            @Param("matchCandidateId") Long matchCandidateId,
            @Param("bankTransactionId") Long bankTransactionId
    );
}
