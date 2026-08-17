package org.teamsai.saibackend.domain.matching.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.matching.dto.request.MatchingReviewSearchCondition;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;

import java.util.List;

@Mapper
public interface BankTransactionMatchingReviewQueryMapper {

    List<BankTransactionDTO> search(
            @Param("userId") Long userId,
            @Param("condition") MatchingReviewSearchCondition condition
    );

    long count(
            @Param("userId") Long userId,
            @Param("condition") MatchingReviewSearchCondition condition
    );
}
