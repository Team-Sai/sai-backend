package org.teamsai.saibackend.domain.user;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.dto.response.UserResponse;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String USER_TOKEN = "SAI-ABCDEFGH";
    private static final String USER_KEY = "mock-bank-user-key";
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("내 정보 조회")
    class GetMyInfo {

        @Test
        @DisplayName("사용자 키로 회원을 조회해 응답 DTO로 반환한다")
        void getMyInfoSuccess() {
            given(userMapper.findById(USER_ID))
                    .willReturn(Optional.of(createUser()));

            UserResponse response = userService.getMyInfo(USER_ID);

            assertThat(response.getUserToken()).isEqualTo(USER_TOKEN);   // USER_KEY → USER_TOKEN
            assertThat(response.getEmail()).isEqualTo("user@example.com");
            assertThat(response.getName()).isEqualTo("김사이");
            assertThat(response.getBirthDate()).isEqualTo(LocalDate.of(2002, 10, 22));        }

        @Test
        @DisplayName("사용자 키에 해당하는 회원이 없으면 예외가 발생한다")
        void getMyInfoFailsWhenUserDoesNotExist() {
            given(userMapper.findById(USER_ID))
                    .willReturn(Optional.empty());

            assertUserNotFound(() -> userService.getMyInfo(USER_ID));
        }
    }

    @Nested
    @DisplayName("회원 탈퇴")
    class Withdraw {

        @Test
        @DisplayName("사용자 ID로 회원을 바로 삭제한다")
        void withdrawSuccess() {
            given(userMapper.deleteByUserId(USER_ID)).willReturn(1);

            userService.withdraw(USER_ID);

            verify(userMapper).deleteByUserId(USER_ID);
        }

        @Test
        @DisplayName("삭제된 행이 0개이면 회원 없음 예외가 발생한다")
        void withdrawFailsWhenUserDoesNotExist() {
            given(userMapper.deleteByUserId(USER_ID)).willReturn(0);

            assertUserNotFound(() -> userService.withdraw(USER_ID));

            verify(userMapper).deleteByUserId(USER_ID);
        }
    }

    private void assertUserNotFound(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(UserErrorCode.USER_NOT_FOUND)
                );
    }

    private UserDTO createUser() {
        return UserDTO.builder()
                .userId(USER_ID)
                .userToken(USER_TOKEN)
                .userKey(USER_KEY)
                .email("user@example.com")
                .password("encoded-password")
                .name("김사이")
                .birthDate(LocalDate.of(2002, 10, 22))
                .build();
    }

    @Test
    @DisplayName("회원토큰으로 요청 대상 회원을 조회한다")
    void findRequestTargetSuccess() {
        Long requesterUserId = 1L;
        String userToken = "USER-ABCD1234";

        UserDTO targetUser = UserDTO.builder()
                .userId(2L)
                .userToken(userToken)
                .name("김사이")
                .build();

        when(userMapper.findByUserToken(userToken))
                .thenReturn(Optional.of(targetUser));

        UserDTO result =
                userService.findRequestTarget(
                        requesterUserId,
                        userToken
                );


        assertThat(result.getUserId()).isEqualTo(2L);
        assertThat(result.getUserToken()).isEqualTo(userToken);
        assertThat(result.getName()).isEqualTo("김사이");

        verify(userMapper).findByUserToken(userToken);
    }

    @Test
    @DisplayName("회원토큰과 일치하는 회원이 없으면 예외가 발생한다")
    void findRequestTargetNotFound() {
        Long requesterUserId = 1L;
        String userToken = "UNKNOWN-TOKEN";

        when(userMapper.findByUserToken(userToken))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> userService.findRequestTarget(
                        requesterUserId,
                        userToken
                )
        ).isInstanceOf(DomainException.class);

        verify(userMapper).findByUserToken(userToken);
    }

    @Test
    @DisplayName("본인의 회원토큰을 요청 대상으로 선택하면 예외가 발생한다")
    void findRequestTargetSelf() {
        Long requesterUserId = 1L;
        String userToken = "USER-MY-TOKEN";

        UserDTO requester = UserDTO.builder()
                .userId(requesterUserId)
                .userToken(userToken)
                .name("김사이")
                .build();

        when(userMapper.findByUserToken(userToken))
                .thenReturn(Optional.of(requester));

        assertThatThrownBy(
                () -> userService.findRequestTarget(
                        requesterUserId,
                        userToken
                )
        ).isInstanceOf(DomainException.class);

        verify(userMapper).findByUserToken(userToken);
    }

}