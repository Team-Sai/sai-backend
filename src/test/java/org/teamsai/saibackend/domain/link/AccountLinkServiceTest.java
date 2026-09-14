package org.teamsai.saibackend.domain.link;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.link.service.AccountLinkService;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.List;

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
    @InjectMocks
    private AccountLinkService accountLinkService;

    private static final Long USER_ID = 1L;
    private static final String NEW_KEY = "mb_newkey";
    private static final String OLD_KEY = "mb_oldkey";

    @Nested
    @DisplayName("completeLink(userId, userKey, accountIds)")
    class CompleteLink {

        @Test
        @DisplayName("기존 키가 없으면(최초 연동) 정상적으로 갱신되고 계좌가 연동된다")
        void linksSuccessfullyWhenNoPreviousKey() {
            given(userMapper.findUserKeyByUserId(USER_ID)).willReturn(null);
            given(userMapper.updateUserKeyByUserId(USER_ID, NEW_KEY, null)).willReturn(1);

            accountLinkService.completeLink(USER_ID, NEW_KEY, List.of(1L));

            verify(linkedBankAccountService).linkAccountsByIds(USER_ID, NEW_KEY, List.of(1L));
        }

        @Test
        @DisplayName("기존 키와 새 키가 같아도 정상적으로 갱신되고 계좌가 연동된다")
        void linksSuccessfullyWhenKeyUnchanged() {
            given(userMapper.findUserKeyByUserId(USER_ID)).willReturn(NEW_KEY);
            given(userMapper.updateUserKeyByUserId(USER_ID, NEW_KEY, NEW_KEY)).willReturn(1);

            accountLinkService.completeLink(USER_ID, NEW_KEY, List.of(1L));

            verify(linkedBankAccountService).linkAccountsByIds(USER_ID, NEW_KEY, List.of(1L));
        }

        @Test
        @DisplayName("동시 요청 경합으로 userKey 갱신이 0건이면 LINK_KEY_UPDATE_CONFLICT 예외를 던진다 (revoke는 호출부 책임)")
        void throwsConflictWhenUpdateAffectsZeroRowsDueToRace() {
            given(userMapper.findUserKeyByUserId(USER_ID)).willReturn(OLD_KEY);
            given(userMapper.updateUserKeyByUserId(USER_ID, NEW_KEY, OLD_KEY)).willReturn(0);

            assertThatThrownBy(() -> accountLinkService.completeLink(USER_ID, NEW_KEY, List.of(1L)))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.LINK_KEY_UPDATE_CONFLICT);

            // 새로 발급된 키의 revoke는 AccountLinkFlowController가 DomainException catch 시
            // 일괄 처리하므로, 여기서는 서비스가 직접 revoke를 호출하지 않는다.
            verify(linkedBankAccountService, never()).linkAccountsByIds(anyLong(), anyString(), anyList());
        }

        @Test
        @DisplayName("계좌 연동이 실패하면 예외가 그대로 전파된다")
        void propagatesExceptionWhenLinkAccountsFails() {
            given(userMapper.findUserKeyByUserId(USER_ID)).willReturn(OLD_KEY);
            given(userMapper.updateUserKeyByUserId(USER_ID, NEW_KEY, OLD_KEY)).willReturn(1);
            willThrow(new RuntimeException("mock-bank 조회 실패"))
                    .given(linkedBankAccountService).linkAccountsByIds(USER_ID, NEW_KEY, List.of(1L));

            assertThatThrownBy(() -> accountLinkService.completeLink(USER_ID, NEW_KEY, List.of(1L)))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}