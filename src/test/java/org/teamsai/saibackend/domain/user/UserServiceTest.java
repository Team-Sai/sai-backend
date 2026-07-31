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

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    private static final String USER_KEY = "SAI-ABCDEFGH";

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
            given(userMapper.findByUserKey(USER_KEY))
                    .willReturn(Optional.of(createUser()));

            UserResponse response = userService.getMyInfo(USER_KEY);

            assertThat(response.getUserKey()).isEqualTo(USER_KEY);
            assertThat(response.getEmail()).isEqualTo("user@example.com");
            assertThat(response.getName()).isEqualTo("김사이");
            assertThat(response.getBirthDate())
                    .isEqualTo(LocalDate.of(2002, 10, 22));
        }

        @Test
        @DisplayName("사용자 키에 해당하는 회원이 없으면 예외가 발생한다")
        void getMyInfoFailsWhenUserDoesNotExist() {
            given(userMapper.findByUserKey(USER_KEY))
                    .willReturn(Optional.empty());

            assertUserNotFound(() -> userService.getMyInfo(USER_KEY));
        }
    }

    @Nested
    @DisplayName("회원 탈퇴")
    class Withdraw {

        @Test
        @DisplayName("사용자 키로 회원을 바로 삭제한다")
        void withdrawSuccess() {
            given(userMapper.deleteByUserKey(USER_KEY)).willReturn(1);

            userService.withdraw(USER_KEY);

            verify(userMapper).deleteByUserKey(USER_KEY);
        }

        @Test
        @DisplayName("삭제된 행이 0개이면 회원 없음 예외가 발생한다")
        void withdrawFailsWhenUserDoesNotExist() {
            given(userMapper.deleteByUserKey(USER_KEY)).willReturn(0);

            assertUserNotFound(() -> userService.withdraw(USER_KEY));

            verify(userMapper).deleteByUserKey(USER_KEY);
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
                .userId(1L)
                .userKey(USER_KEY)
                .email("user@example.com")
                .password("encoded-password")
                .name("김사이")
                .birthDate(LocalDate.of(2002, 10, 22))
                .build();
    }
}