package org.teamsai.saibackend.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.notification.dto.NotificationDTO;
import org.teamsai.saibackend.domain.notification.dto.response.NotificationResponse;
import org.teamsai.saibackend.domain.notification.mapper.NotificationMapper;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.notification.type.NotificationType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 단위 테스트")
class NotificationServiceTest {

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("정산 참여자에게 알림을 생성한다 (secondaryReferenceId 없이)")
    void createNotificationSuccess() {
        Long userId = 2L;
        Long settlementId = 10L;

        notificationService.create(
                userId,
                NotificationType.SETTLEMENT_PARTICIPANT_ADDED,
                "새로운 정산에 참여자로 등록되었습니다.",
                "정산 금액 30000원이 등록되었습니다.",
                settlementId
        );

        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationMapper).insert(captor.capture());

        NotificationDTO notification = captor.getValue();

        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.SETTLEMENT_PARTICIPANT_ADDED);
        assertThat(notification.getTitle()).isEqualTo("새로운 정산에 참여자로 등록되었습니다.");
        assertThat(notification.getContent()).isEqualTo("정산 금액 30000원이 등록되었습니다.");
        assertThat(notification.getReferenceId()).isEqualTo(settlementId);
        assertThat(notification.getCreatedAt()).isNotNull();

        // 5개짜리 오버로드를 쓰면 secondaryReferenceId는 항상 null이어야 한다
        assertThat(notification.getSecondaryReferenceId()).isNull();
    }

    @Test
    @DisplayName("계약 변경 요청 알림은 secondaryReferenceId(changeRequestId)까지 함께 저장한다")
    void createNotificationWithSecondaryReferenceIdSuccess() {
        Long debtorId = 3L;
        Long contractId = 100L;
        Long changeRequestId = 7L;

        notificationService.create(
                debtorId,
                NotificationType.CONTRACT_CHANGE,
                "계약 변경 요청",
                "홍길동님으로부터 계약 내용 변경 요청이 도착했습니다.",
                contractId,
                changeRequestId
        );

        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationMapper).insert(captor.capture());

        NotificationDTO notification = captor.getValue();

        assertThat(notification.getReferenceId()).isEqualTo(contractId);
        assertThat(notification.getSecondaryReferenceId()).isEqualTo(changeRequestId);
    }

    @Test
    @DisplayName("로그인 사용자의 알림 목록을 조회한다")
    void getNotificationsSuccess() {
        Long userId = 2L;
        LocalDateTime createdAt = LocalDateTime.now();

        List<NotificationResponse> expected =
                List.of(
                        new NotificationResponse(
                                1L,
                                NotificationType
                                        .SETTLEMENT_PARTICIPANT_ADDED,
                                "새로운 정산에 참여자로 등록되었습니다.",
                                "정산 금액 30000원이 등록되었습니다.",
                                10L,
                                null,          // ← 이 줄 추가 (secondaryReferenceId)
                                null,
                                null,
                                null,
                                null,
                                false,
                                createdAt
                        )
                );

        when(notificationMapper.findAllByUserId(userId)).thenReturn(expected);

        List<NotificationResponse> result = notificationService.getNotifications(userId);

        assertThat(result).isEqualTo(expected);
        verify(notificationMapper).findAllByUserId(userId);
    }

    @Test
    @DisplayName("같은 은행 거래의 매칭 검토 알림이 없으면 생성한다")
    void createsMatchingReviewNotificationWhenItDoesNotExist() {
        when(notificationMapper.existsByUserIdAndTypeAndReferenceId(
                2L,
                NotificationType.BANK_TRANSACTION_MATCHING_REVIEW,
                100L
        )).thenReturn(false);

        notificationService.createIfAbsent(
                2L,
                NotificationType.BANK_TRANSACTION_MATCHING_REVIEW,
                "입금 거래 확인이 필요합니다.",
                "정산과 차용증 후보가 모두 발견되었습니다.",
                100L,
                10L
        );

        verify(notificationMapper).insert(
                org.mockito.ArgumentMatchers.argThat(notification ->
                        notification.getUserId().equals(2L)
                                && notification.getReferenceId().equals(100L)
                                && notification.getSecondaryReferenceId()
                                .equals(10L)
                )
        );
    }

    @Test
    @DisplayName("같은 은행 거래의 매칭 검토 알림이 있으면 중복 생성하지 않는다")
    void doesNotCreateDuplicatedMatchingReviewNotification() {
        when(notificationMapper.existsByUserIdAndTypeAndReferenceId(
                2L,
                NotificationType.BANK_TRANSACTION_MATCHING_REVIEW,
                100L
        )).thenReturn(true);

        notificationService.createIfAbsent(
                2L,
                NotificationType.BANK_TRANSACTION_MATCHING_REVIEW,
                "입금 거래 확인이 필요합니다.",
                "정산과 차용증 후보가 모두 발견되었습니다.",
                100L,
                10L
        );

        verify(notificationMapper, never()).insert(
                org.mockito.ArgumentMatchers.any()
        );
    }
}
