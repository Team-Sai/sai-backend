package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.payment.service.PaymentService;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementInvitationDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementInvitationMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationResponseService;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationValidator;
import org.teamsai.saibackend.domain.settlement.type.SettlementInvitationStatus;
import org.teamsai.saibackend.global.exception.DomainException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementInvitationResponseService 단위 테스트")
class SettlementInvitationResponseServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long INVITATION_ID = 3L;
    private static final Long SETTLEMENT_ID = 2L;

    @Mock
    private SettlementInvitationMapper invitationMapper;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private SettlementInvitationValidator invitationValidator;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private SettlementInvitationResponseService responseService;

    @Test
    @DisplayName("정산 초대를 수락하면 초대 상태만 변경한다")
    void acceptSuccess() {
        SettlementInvitationDTO invitation =
                createInvitation();

        SettlementDTO settlement =
                SettlementDTO.builder()
                        .settlementId(SETTLEMENT_ID)
                        .build();

        when(invitationMapper.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation));

        when(settlementMapper.findByIdForUpdate(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));

        when(invitationMapper.accept(
                eq(INVITATION_ID),
                any(LocalDateTime.class)
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
                .validateAcceptableSettlement(
                        settlement
                );

        ArgumentCaptor<LocalDateTime> acceptedAtCaptor =
                ArgumentCaptor.forClass(
                        LocalDateTime.class
                );

        verify(invitationMapper)
                .accept(
                        eq(INVITATION_ID),
                        acceptedAtCaptor.capture()
                );

        assertThat(acceptedAtCaptor.getValue())
                .isNotNull();

        /*
         * 참여자와 납부 의무는 정산 생성 시 이미 생성됐다.
         * 수락 과정에서는 납부 의무를 새로 만들거나 변경하지 않는다.
         */
        verifyNoInteractions(paymentService);
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
                invitationValidator,
                paymentService
        );

        verify(invitationMapper, never())
                .accept(
                        eq(INVITATION_ID),
                        any(LocalDateTime.class)
                );
    }

    @Test
    @DisplayName("초대 수락 상태 변경에 실패하면 예외가 발생한다")
    void acceptFailWhenInvitationUpdateFailed() {
        SettlementInvitationDTO invitation =
                createInvitation();

        SettlementDTO settlement =
                SettlementDTO.builder()
                        .settlementId(SETTLEMENT_ID)
                        .build();

        when(invitationMapper.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation));

        when(settlementMapper.findByIdForUpdate(SETTLEMENT_ID))
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

        verify(invitationMapper)
                .accept(
                        eq(INVITATION_ID),
                        any(LocalDateTime.class)
                );

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName(
            "정산 초대를 거절하면 초대 상태를 변경하고 납부 의무를 확인 필요 상태로 변경한다"
    )
    void rejectSuccess() {
        SettlementInvitationDTO invitation =
                createInvitation();

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

        verify(paymentService)
                .markObligationNeedsCheckByInvitationId(
                        INVITATION_ID
                );

        verifyNoInteractions(settlementMapper);
    }

    @Test
    @DisplayName(
            "초대 거절 상태 변경에 실패하면 납부 의무 상태를 변경하지 않는다"
    )
    void rejectFailWhenInvitationUpdateFailed() {
        SettlementInvitationDTO invitation =
                createInvitation();

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

        verify(invitationMapper)
                .reject(INVITATION_ID);

        verifyNoInteractions(
                settlementMapper,
                paymentService
        );
    }

    @Test
    @DisplayName("정산 초대 수락 시 정산을 잠금 조회한다")
    void acceptUsesSettlementForUpdate() {
        SettlementInvitationDTO invitation =
                createInvitation();

        SettlementDTO settlement =
                SettlementDTO.builder()
                        .settlementId(SETTLEMENT_ID)
                        .build();

        when(invitationMapper.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation));

        when(settlementMapper.findByIdForUpdate(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));

        when(invitationMapper.accept(
                eq(INVITATION_ID),
                any(LocalDateTime.class)
        )).thenReturn(1);

        responseService.accept(
                USER_ID,
                INVITATION_ID
        );

        verify(settlementMapper)
                .findByIdForUpdate(
                        SETTLEMENT_ID
                );

        verify(settlementMapper, never())
                .findById(anyLong());

        verify(invitationValidator)
                .validateAcceptableSettlement(
                        settlement
                );

        verify(invitationMapper)
                .accept(
                        eq(INVITATION_ID),
                        any(LocalDateTime.class)
                );

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName(
            "잠금 조회에서 정산을 찾지 못하면 초대 상태를 변경하지 않는다"
    )
    void acceptFailWhenSettlementNotFoundForUpdate() {
        SettlementInvitationDTO invitation =
                createInvitation();

        when(invitationMapper.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation));

        when(settlementMapper.findByIdForUpdate(SETTLEMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> responseService.accept(
                        USER_ID,
                        INVITATION_ID
                )
        ).isInstanceOf(DomainException.class);

        verify(settlementMapper)
                .findByIdForUpdate(
                        SETTLEMENT_ID
                );

        verify(invitationMapper, never())
                .accept(
                        anyLong(),
                        any(LocalDateTime.class)
                );

        verifyNoInteractions(paymentService);
    }

    private SettlementInvitationDTO createInvitation() {
        return SettlementInvitationDTO.builder()
                .invitationId(INVITATION_ID)
                .settlementId(SETTLEMENT_ID)
                .invitedUserId(USER_ID)
                .invitationStatus(
                        SettlementInvitationStatus.INVITED
                )
                .build();
    }
}