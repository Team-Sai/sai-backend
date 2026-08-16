package org.teamsai.saibackend.domain.link;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.link.event.PreviousUserKeyRevokedEvent;
import org.teamsai.saibackend.domain.link.service.AccountLinkService;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.client.MockBankClient;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountLinkService 단위 테스트")
class AccountLinkServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private LinkedBankAccountService linkedBankAccountService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MockBankClient mockBankClient;
    @InjectMocks
    private AccountLinkService accountLinkService;

    private static final Long USER_ID = 1L;
    private static final String NEW_KEY = "mb_newkey";
    private static final String OLD_KEY = "mb_oldkey";

    @Nested
    @DisplayName("completeLink(userId, userKey, accountIds)")
    class CompleteLink {

        @Test
        @DisplayName("기존 키가 있으면 갱신 후 이전 키 revoke 이벤트를 발행한다")
        void publishesRevokeEventWhenPreviousKeyExists() {
            given(userMapper.findUserKeyByUserId(USER_ID)).willReturn(OLD_KEY);
            given(userMapper.updateUserKeyByUserId(USER_ID, NEW_KEY, OLD_KEY)).willReturn(1);

            accountLinkService.completeLink(USER_ID, NEW_KEY, List.of(1L, 2L));

            verify(linkedBankAccountService).linkAccountsByIds(USER_ID, NEW_KEY, List.of(1L, 2L));

            ArgumentCaptor<PreviousUserKeyRevokedEvent> captor =
                    ArgumentCaptor.forClass(PreviousUserKeyRevokedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
            assertThat(captor.getValue().previousUserKey()).isEqualTo(OLD_KEY);
        }

        @Test
        @DisplayName("기존 키가 없으면(최초 연동) revoke 이벤트를 발행하지 않는다")
        void doesNotPublishEventWhenNoPreviousKey() {
            given(userMapper.findUserKeyByUserId(USER_ID)).willReturn(null);
            given(userMapper.updateUserKeyByUserId(USER_ID, NEW_KEY, null)).willReturn(1);

            accountLinkService.completeLink(USER_ID, NEW_KEY, List.of(1L));

            verify(eventPublisher, never()).publishEvent(anyEvent());
        }

        @Test
        @DisplayName("기존 키와 새 키가 같으면 revoke 이벤트를 발행하지 않는다")
        void doesNotPublishEventWhenKeyUnchanged() {
            given(userMapper.findUserKeyByUserId(USER_ID)).willReturn(NEW_KEY);
            given(userMapper.updateUserKeyByUserId(USER_ID, NEW_KEY, NEW_KEY)).willReturn(1);

            accountLinkService.completeLink(USER_ID, NEW_KEY, List.of(1L));

            verify(eventPublisher, never()).publishEvent(anyEvent());
        }

        @Test
        @DisplayName("동시 요청 경합으로 userKey 갱신이 0건이면 새 키를 revoke하고 LINK_KEY_UPDATE_CONFLICT 예외를 던진다")
        void revokesNewKeyAndThrowsWhenUpdateAffectsZeroRowsDueToRace() {
            given(userMapper.findUserKeyByUserId(USER_ID)).willReturn(OLD_KEY);
            given(userMapper.updateUserKeyByUserId(USER_ID, NEW_KEY, OLD_KEY)).willReturn(0);

            assertThatThrownBy(() -> accountLinkService.completeLink(USER_ID, NEW_KEY, List.of(1L)))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.LINK_KEY_UPDATE_CONFLICT);

            verify(mockBankClient).revokeUserKey(NEW_KEY);
            verify(linkedBankAccountService, never()).linkAccountsByIds(anyLong(), anyString(), anyList());
            verify(eventPublisher, never()).publishEvent(any(PreviousUserKeyRevokedEvent.class));
        }

        @Test
        @DisplayName("계좌 연동이 실패하면 예외가 전파되고 revoke 이벤트는 발행되지 않는다")
        void doesNotPublishEventWhenLinkAccountsFails() {
            given(userMapper.findUserKeyByUserId(USER_ID)).willReturn(OLD_KEY);
            given(userMapper.updateUserKeyByUserId(USER_ID, NEW_KEY, OLD_KEY)).willReturn(1);
            willThrow(new RuntimeException("mock-bank 조회 실패"))
                    .given(linkedBankAccountService).linkAccountsByIds(USER_ID, NEW_KEY, List.of(1L));

            assertThatThrownBy(() -> accountLinkService.completeLink(USER_ID, NEW_KEY, List.of(1L)))
                    .isInstanceOf(RuntimeException.class);

            verify(eventPublisher, never()).publishEvent(anyEvent());
        }
    }

    private static PreviousUserKeyRevokedEvent anyEvent() {
        return any(PreviousUserKeyRevokedEvent.class);
    }
}