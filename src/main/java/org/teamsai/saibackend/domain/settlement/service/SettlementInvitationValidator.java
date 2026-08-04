package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementInvitationMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SettlementInvitationValidator {

    private final SettlementParticipantMapper participantMapper;
    private final SettlementInvitationMapper invitationMapper;

    public void validateInvitableSettlement(
            SettlementDTO settlement,
            Long ownerId
    ) {
        validateOwner(settlement, ownerId);
        validateInProgress(settlement);
    }

    public void validateInviteTarget(
            Long settlementId,
            Long invitedUserId
    ) {
        validateNotParticipant(
                settlementId,
                invitedUserId
        );

        validateNoPendingInvitation(
                settlementId,
                invitedUserId
        );
    }

    private void validateOwner(
            SettlementDTO settlement,
            Long ownerId
    ) {
        if (!Objects.equals(
                settlement.getOwnerId(),
                ownerId
        )) {
            throw SettlementErrorCode
                    .SETTLEMENT_ACCESS_DENIED
                    .toException();
        }
    }

    private void validateInProgress(
            SettlementDTO settlement
    ) {
        if (settlement.getSettlementStatus()
                == SettlementStatus.CLOSED) {

            throw SettlementErrorCode
                    .ALREADY_CLOSED_SETTLEMENT
                    .toException();
        }
    }

    private void validateNotParticipant(
            Long settlementId,
            Long invitedUserId
    ) {
        boolean alreadyParticipant =
                participantMapper.existsActiveParticipant(
                        settlementId,
                        invitedUserId
                );

        if (alreadyParticipant) {
            throw SettlementErrorCode
                    .ALREADY_SETTLEMENT_PARTICIPANT
                    .toException();
        }
    }

    private void validateNoPendingInvitation(
            Long settlementId,
            Long invitedUserId
    ) {
        boolean duplicateInvitation =
                invitationMapper.existsInvitedInvitation(
                        settlementId,
                        invitedUserId
                );

        if (duplicateInvitation) {
            throw SettlementErrorCode
                    .DUPLICATE_SETTLEMENT_INVITATION
                    .toException();
        }
    }
}