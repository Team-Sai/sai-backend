package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.global.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementInvitationValidatorTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long SETTLEMENT_ID = 10L;
    private static final Long INVITED_USER_ID = 3L;

    @Mock
    private SettlementParticipantMapper participantMapper;

    @Mock
    private SettlementInvitationMapper invitationMapper;

    private SettlementInvitationValidator invitationValidator;

    @BeforeEach
    void setUp() {
        invitationValidator =
                new SettlementInvitationValidator(
                        participantMapper,
                        invitationMapper
                );
    }

    @Test
    @DisplayName("정산 소유자이며 진행 중인 정산이면 초대할 수 있다")
    void validateInvitableSettlementSuccess() {
        SettlementDTO settlement = mock(SettlementDTO.class);

        when(settlement.getOwnerId())
                .thenReturn(OWNER_ID);

        when(settlement.getSettlementStatus())
                .thenReturn(SettlementStatus.IN_PROGRESS);

        assertThatCode(
                () -> invitationValidator
                        .validateInvitableSettlement(
                                settlement,
                                OWNER_ID
                        )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정산 소유자가 아니면 초대할 수 없다")
    void validateInvitableSettlementFailWhenNotOwner() {
        SettlementDTO settlement = mock(SettlementDTO.class);

        when(settlement.getOwnerId())
                .thenReturn(OWNER_ID);

        assertThatThrownBy(
                () -> invitationValidator
                        .validateInvitableSettlement(
                                settlement,
                                OTHER_USER_ID
                        )
        ).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("종료된 정산에는 참여자를 초대할 수 없다")
    void validateInvitableSettlementFailWhenClosed() {
        SettlementDTO settlement = mock(SettlementDTO.class);

        when(settlement.getOwnerId())
                .thenReturn(OWNER_ID);

        when(settlement.getSettlementStatus())
                .thenReturn(SettlementStatus.CLOSED);

        assertThatThrownBy(
                () -> invitationValidator
                        .validateInvitableSettlement(
                                settlement,
                                OWNER_ID
                        )
        ).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("이미 참여 중인 회원은 초대할 수 없다")
    void validateInviteTargetFailWhenAlreadyParticipant() {
        when(
                participantMapper.existsActiveParticipant(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                )
        ).thenReturn(true);

        assertThatThrownBy(
                () -> invitationValidator.validateInviteTarget(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                )
        ).isInstanceOf(DomainException.class);

        verify(
                invitationMapper,
                never()
        ).existsInvitedInvitation(
                SETTLEMENT_ID,
                INVITED_USER_ID
        );
    }

    @Test
    @DisplayName("대기 중인 동일 초대가 있으면 다시 초대할 수 없다")
    void validateInviteTargetFailWhenDuplicateInvitation() {
        when(
                participantMapper.existsActiveParticipant(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                )
        ).thenReturn(false);

        when(
                invitationMapper.existsInvitedInvitation(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                )
        ).thenReturn(true);

        assertThatThrownBy(
                () -> invitationValidator.validateInviteTarget(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                )
        ).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("참여자와 대기 중 초대가 없으면 초대할 수 있다")
    void validateInviteTargetSuccess() {
        when(
                participantMapper.existsActiveParticipant(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                )
        ).thenReturn(false);

        when(
                invitationMapper.existsInvitedInvitation(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                )
        ).thenReturn(false);

        assertThatCode(
                () -> invitationValidator.validateInviteTarget(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                )
        ).doesNotThrowAnyException();

        verify(participantMapper)
                .existsActiveParticipant(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                );

        verify(invitationMapper)
                .existsInvitedInvitation(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                );
    }

    @Test
    @DisplayName("초대 대상 회원이고 INVITED 상태이면 초대에 응답할 수 있다")
    void validateRespondableInvitationSuccess() {
        SettlementInvitationDTO invitation =
                SettlementInvitationDTO.builder()
                        .invitedUserId(OTHER_USER_ID)
                        .invitationStatus(
                                SettlementInvitationStatus.INVITED
                        )
                        .build();

        assertThatCode(
                () -> invitationValidator
                        .validateRespondableInvitation(
                                invitation,
                                OTHER_USER_ID
                        )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("초대 대상 회원이 아니면 초대를 처리할 수 없다")
    void validateRespondableInvitationFailWhenNotInvitedUser() {
        SettlementInvitationDTO invitation =
                SettlementInvitationDTO.builder()
                        .invitedUserId(INVITED_USER_ID)
                        .invitationStatus(
                                SettlementInvitationStatus.INVITED
                        )
                        .build();

        assertThatThrownBy(
                () -> invitationValidator
                        .validateRespondableInvitation(
                                invitation,
                                OTHER_USER_ID
                        )
        ).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("이미 처리된 초대에는 다시 응답할 수 없다")
    void validateRespondableInvitationFailWhenAlreadyProcessed() {
        SettlementInvitationDTO invitation =
                SettlementInvitationDTO.builder()
                        .invitedUserId(OTHER_USER_ID)
                        .invitationStatus(
                                SettlementInvitationStatus.ACCEPTED
                        )
                        .build();

        assertThatThrownBy(
                () -> invitationValidator
                        .validateRespondableInvitation(
                                invitation,
                                OTHER_USER_ID
                        )
        ).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("진행 중인 정산의 초대는 수락할 수 있다")
    void validateAcceptableSettlementSuccess() {
        SettlementDTO settlement =
                mock(SettlementDTO.class);

        when(settlement.getSettlementStatus())
                .thenReturn(SettlementStatus.IN_PROGRESS);

        assertThatCode(
                () -> invitationValidator
                        .validateAcceptableSettlement(settlement)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("종료된 정산의 초대는 수락할 수 없다")
    void validateAcceptableSettlementFailWhenClosed() {
        SettlementDTO settlement =
                mock(SettlementDTO.class);

        when(settlement.getSettlementStatus())
                .thenReturn(SettlementStatus.CLOSED);

        assertThatThrownBy(
                () -> invitationValidator
                        .validateAcceptableSettlement(settlement)
        ).isInstanceOf(DomainException.class);
    }
}