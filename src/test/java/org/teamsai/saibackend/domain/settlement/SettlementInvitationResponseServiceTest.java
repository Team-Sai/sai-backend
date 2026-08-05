package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementInvitationDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementInvitationMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationResponseService;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationValidator;
import org.teamsai.saibackend.domain.settlement.type.SettlementInvitationStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantRole;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;
import org.teamsai.saibackend.global.exception.DomainException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementInvitationResponseServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long INVITATION_ID = 3L;
    private static final Long SETTLEMENT_ID = 2L;

    @Mock
    private SettlementInvitationMapper invitationMapper;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private SettlementParticipantMapper participantMapper;

    @Mock
    private SettlementInvitationValidator invitationValidator;

    @InjectMocks
    private SettlementInvitationResponseService responseService;

    @Test
    @DisplayName("정산 초대를 수락하면 초대 상태를 변경하고 참여자를 등록한다")
    void acceptSuccess() {
        SettlementInvitationDTO invitation =
                SettlementInvitationDTO.builder()
                        .invitationId(INVITATION_ID)
                        .settlementId(SETTLEMENT_ID)
                        .invitedUserId(USER_ID)
                        .invitationStatus(SettlementInvitationStatus.INVITED)
                        .build();

        SettlementDTO settlement =
                SettlementDTO.builder()
                        .settlementId(SETTLEMENT_ID)
                        .build();

        when(invitationMapper.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation));

        when(settlementMapper.findById(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));

        when(invitationMapper.accept(
                eq(INVITATION_ID),
                any(LocalDateTime.class)
        )).thenReturn(1);

        when(participantMapper.insert(
                any(SettlementParticipantDTO.class)
        )).thenReturn(1);

        responseService.accept(
                USER_ID,
                INVITATION_ID
        );

        verify(invitationValidator)
                .validateRespondableInvitation(
                        invitation,
                        USER_ID
                );

        verify(invitationValidator)
                .validateAcceptableSettlement(settlement);

        ArgumentCaptor<LocalDateTime> acceptedAtCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);

        verify(invitationMapper).accept(
                eq(INVITATION_ID),
                acceptedAtCaptor.capture()
        );

        ArgumentCaptor<SettlementParticipantDTO> participantCaptor =
                ArgumentCaptor.forClass(
                        SettlementParticipantDTO.class
                );

        verify(participantMapper)
                .insert(participantCaptor.capture());

        SettlementParticipantDTO participant =
                participantCaptor.getValue();

        assertThat(participant.getInvitationId())
                .isEqualTo(INVITATION_ID);

        assertThat(participant.getParticipantRole())
                .isEqualTo(SettlementParticipantRole.MEMBER);

        assertThat(participant.getParticipantStatus())
                .isEqualTo(SettlementParticipantStatus.ACTIVE);

        assertThat(participant.getJoinedAt())
                .isEqualTo(acceptedAtCaptor.getValue());
    }

    @Test
    @DisplayName("존재하지 않는 정산 초대는 수락할 수 없다")
    void acceptFailWhenInvitationNotFound() {
        when(invitationMapper.findById(INVITATION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> responseService.accept(
                        USER_ID,
                        INVITATION_ID
                )
        ).isInstanceOf(DomainException.class);

        verifyNoInteractions(
                settlementMapper,
                participantMapper,
                invitationValidator
        );

        verify(
                invitationMapper,
                never()
        ).accept(
                eq(INVITATION_ID),
                any(LocalDateTime.class)
        );
    }

    @Test
    @DisplayName("초대 수락 상태 변경에 실패하면 참여자를 등록하지 않는다")
    void acceptFailWhenInvitationUpdateFailed() {
        SettlementInvitationDTO invitation =
                SettlementInvitationDTO.builder()
                        .invitationId(INVITATION_ID)
                        .settlementId(SETTLEMENT_ID)
                        .invitedUserId(USER_ID)
                        .invitationStatus(SettlementInvitationStatus.INVITED)
                        .build();

        SettlementDTO settlement =
                SettlementDTO.builder()
                        .settlementId(SETTLEMENT_ID)
                        .build();

        when(invitationMapper.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation));

        when(settlementMapper.findById(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));

        when(invitationMapper.accept(
                eq(INVITATION_ID),
                any(LocalDateTime.class)
        )).thenReturn(0);

        assertThatThrownBy(
                () -> responseService.accept(
                        USER_ID,
                        INVITATION_ID
                )
        ).isInstanceOf(DomainException.class);

        verify(
                participantMapper,
                never()
        ).insert(any(SettlementParticipantDTO.class));
    }

    @Test
    @DisplayName("초대 수락 후 참여자 등록에 실패하면 예외가 발생한다")
    void acceptFailWhenParticipantInsertFailed() {
        SettlementInvitationDTO invitation =
                SettlementInvitationDTO.builder()
                        .invitationId(INVITATION_ID)
                        .settlementId(SETTLEMENT_ID)
                        .invitedUserId(USER_ID)
                        .invitationStatus(SettlementInvitationStatus.INVITED)
                        .build();

        SettlementDTO settlement =
                SettlementDTO.builder()
                        .settlementId(SETTLEMENT_ID)
                        .build();

        when(invitationMapper.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation));

        when(settlementMapper.findById(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));

        when(invitationMapper.accept(
                eq(INVITATION_ID),
                any(LocalDateTime.class)
        )).thenReturn(1);

        when(participantMapper.insert(
                any(SettlementParticipantDTO.class)
        )).thenReturn(0);

        assertThatThrownBy(
                () -> responseService.accept(
                        USER_ID,
                        INVITATION_ID
                )
        ).isInstanceOf(DomainException.class);

        verify(participantMapper)
                .insert(any(SettlementParticipantDTO.class));
    }

    @Test
    @DisplayName("정산 초대를 거절하면 초대 상태만 변경한다")
    void rejectSuccess() {
        SettlementInvitationDTO invitation =
                SettlementInvitationDTO.builder()
                        .invitationId(INVITATION_ID)
                        .settlementId(SETTLEMENT_ID)
                        .invitedUserId(USER_ID)
                        .invitationStatus(SettlementInvitationStatus.INVITED)
                        .build();

        when(invitationMapper.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation));

        when(invitationMapper.reject(INVITATION_ID))
                .thenReturn(1);

        responseService.reject(
                USER_ID,
                INVITATION_ID
        );

        verify(invitationValidator)
                .validateRespondableInvitation(
                        invitation,
                        USER_ID
                );

        verify(invitationMapper)
                .reject(INVITATION_ID);

        verifyNoInteractions(
                settlementMapper,
                participantMapper
        );
    }

    @Test
    @DisplayName("초대 거절 상태 변경에 실패하면 예외가 발생한다")
    void rejectFailWhenInvitationUpdateFailed() {
        SettlementInvitationDTO invitation =
                SettlementInvitationDTO.builder()
                        .invitationId(INVITATION_ID)
                        .settlementId(SETTLEMENT_ID)
                        .invitedUserId(USER_ID)
                        .invitationStatus(SettlementInvitationStatus.INVITED)
                        .build();

        when(invitationMapper.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation));

        when(invitationMapper.reject(INVITATION_ID))
                .thenReturn(0);

        assertThatThrownBy(
                () -> responseService.reject(
                        USER_ID,
                        INVITATION_ID
                )
        ).isInstanceOf(DomainException.class);

        verifyNoInteractions(
                settlementMapper,
                participantMapper
        );
    }
}