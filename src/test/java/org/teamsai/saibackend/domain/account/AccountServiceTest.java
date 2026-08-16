package org.teamsai.saibackend.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.link.dto.response.UserKeyResponse;
import org.teamsai.saibackend.domain.link.mapper.LinkMapper;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.domain.account.service.AccountService;
import org.teamsai.saibackend.global.client.MockBankClient;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 단위 테스트")
class AccountServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private LinkMapper linkMapper;
    @Mock
    private MockBankClient mockBankClient;
    @InjectMocks
    private AccountService accountService;

    private static final Long USER_ID = 1L;
    private static final String USER_NAME = "홍길동";
    private static final String USER_TOKEN = "SAI-ABCDEFGH";
    private static final String NEW_KEY = "mb_newkey12345678";
    private static final String EXISTING_KEY = "mb_existingkey";

    private UserDTO createUser(String userKey) {
        return UserDTO.builder()
                .userId(USER_ID)
                .name(USER_NAME)
                .userToken(USER_TOKEN)
                .userKey(userKey)
                .build();
    }

    @Nested
    @DisplayName("issueOrGetUserKey(userId)")
    class IssueOrGetUserKey {

        @Test
        @DisplayName("이미 userKey가 있으면 그대로 반환하고 mock-bank는 호출하지 않는다")
        void returnsExistingKeyWithoutCallingMockBank() {
            given(userMapper.findById(USER_ID)).willReturn(Optional.of(createUser(EXISTING_KEY)));

            UserKeyResponse response = accountService.issueOrGetUserKey(USER_ID);

            assertThat(response.userKey()).isEqualTo(EXISTING_KEY);
            verify(mockBankClient, never()).requestUserKey(anyString(), anyString());
            verify(mockBankClient, never()).confirmUserKey(anyString());
            verify(linkMapper, never()).updateUserKey(anyLong(), anyString());
        }

        @Test
        @DisplayName("userKey가 없으면 발급 → confirm → 로컬 저장 순서로 성공한다")
        void issuesConfirmsAndSavesNewKey() {
            given(userMapper.findById(USER_ID)).willReturn(Optional.of(createUser(null)));
            given(mockBankClient.requestUserKey(USER_NAME, USER_TOKEN)).willReturn(NEW_KEY);

            UserKeyResponse response = accountService.issueOrGetUserKey(USER_ID);

            assertThat(response.userKey()).isEqualTo(NEW_KEY);

            var inOrder = org.mockito.Mockito.inOrder(mockBankClient, linkMapper);
            inOrder.verify(mockBankClient).requestUserKey(USER_NAME, USER_TOKEN);
            inOrder.verify(mockBankClient).confirmUserKey(NEW_KEY);
            inOrder.verify(linkMapper).updateUserKey(USER_ID, NEW_KEY);
        }

        @Test
        @DisplayName("confirm이 실패하면 BANK_SERVER_UNAVAILABLE 예외를 던지고 로컬에는 저장하지 않는다")
        void throwsExceptionAndDoesNotSaveWhenConfirmFails() {
            given(userMapper.findById(USER_ID)).willReturn(Optional.of(createUser(null)));
            given(mockBankClient.requestUserKey(USER_NAME, USER_TOKEN)).willReturn(NEW_KEY);
            willThrow(new RuntimeException("mock-bank 다운"))
                    .given(mockBankClient).confirmUserKey(NEW_KEY);

            assertThatThrownBy(() -> accountService.issueOrGetUserKey(USER_ID))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.BANK_SERVER_UNAVAILABLE);

            verify(linkMapper, never()).updateUserKey(anyLong(), anyString());
        }

        @Test
        @DisplayName("일치하는 회원이 없으면 USER_NOT_FOUND 예외가 발생하고 mock-bank는 호출되지 않는다")
        void throwsUserNotFoundWhenUserDoesNotExist() {
            given(userMapper.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.issueOrGetUserKey(USER_ID))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND);

            verify(mockBankClient, never()).requestUserKey(anyString(), anyString());
            verify(mockBankClient, never()).confirmUserKey(anyString());
        }

        @Test
        @DisplayName("confirm은 성공했지만 로컬 저장이 실패하면 revoke를 요청하고 BANK_SERVER_UNAVAILABLE 예외를 던진다")
        void revokesConfirmedKeyWhenLocalSaveFails() {
            given(userMapper.findById(USER_ID)).willReturn(Optional.of(createUser(null)));
            given(mockBankClient.requestUserKey(USER_NAME, USER_TOKEN)).willReturn(NEW_KEY);
            willThrow(new org.springframework.dao.DataAccessResourceFailureException("DB 연결 실패"))
                    .given(linkMapper).updateUserKey(USER_ID, NEW_KEY);

            assertThatThrownBy(() -> accountService.issueOrGetUserKey(USER_ID))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.BANK_SERVER_UNAVAILABLE);

            var inOrder = org.mockito.Mockito.inOrder(mockBankClient, linkMapper);
            inOrder.verify(mockBankClient).confirmUserKey(NEW_KEY);
            inOrder.verify(linkMapper).updateUserKey(USER_ID, NEW_KEY);
            inOrder.verify(mockBankClient).revokeUserKey(NEW_KEY);
        }

        @Test
        @DisplayName("로컬 저장 실패 후 revoke마저 실패해도 예외는 BANK_SERVER_UNAVAILABLE로 동일하게 던져진다")
        void stillThrowsExpectedExceptionWhenRevokeAlsoFails() {
            given(userMapper.findById(USER_ID)).willReturn(Optional.of(createUser(null)));
            given(mockBankClient.requestUserKey(USER_NAME, USER_TOKEN)).willReturn(NEW_KEY);
            willThrow(new org.springframework.dao.DataAccessResourceFailureException("DB 연결 실패"))
                    .given(linkMapper).updateUserKey(USER_ID, NEW_KEY);
            willThrow(new RuntimeException("mock-bank 다운"))
                    .given(mockBankClient).revokeUserKey(NEW_KEY);

            assertThatThrownBy(() -> accountService.issueOrGetUserKey(USER_ID))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.BANK_SERVER_UNAVAILABLE);

            verify(mockBankClient).revokeUserKey(NEW_KEY);
        }
    }
}