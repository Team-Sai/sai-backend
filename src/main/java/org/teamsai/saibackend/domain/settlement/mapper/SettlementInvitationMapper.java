package org.teamsai.saibackend.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.settlement.dto.SettlementInvitationDTO;
import org.teamsai.saibackend.domain.settlement.dto.response.ReceivedSettlementInvitationResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface SettlementInvitationMapper {

    int insert(SettlementInvitationDTO invitation);

    boolean existsInvitedInvitation(
            @Param("settlementId") Long settlementId,
            @Param("userId") Long userId
    );

    Optional<SettlementInvitationDTO> findById(
            @Param("invitationId") Long invitationId
    );

    List<ReceivedSettlementInvitationResponse> findReceivedInvitations(
            @Param("userId") Long userId
    );

    int accept(
            @Param("invitationId") Long invitationId,
            @Param("acceptedAt")LocalDateTime acceptedAt
    );

    int reject(
            @Param("invitationId") Long invitationId
    );
}
