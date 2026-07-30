package org.teamsai.saibackend.domain.user;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.domain.user.service.UserValidator;
import org.teamsai.saibackend.global.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserValidator 단위 테스트")
class UserValidatorTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserValidator userValidator;

    @Nested
    @DisplayName("회원가입 검증")
    class ValidateSignUp {

        @Test
        @DisplayName("중복되지 않은 이메일이면 검증을 통과한다")
        void acceptsAvailableEmail() {
            given(userMapper.existsByEmail("user@example.com"))
                    .willReturn(false);

            assertThatCode(
                    () -> userValidator.validateSignUp("user@example.com")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("이미 가입된 이메일이면 중복 이메일 예외가 발생한다")
        void rejectsDuplicateEmail() {
            given(userMapper.existsByEmail("user@example.com"))
                    .willReturn(true);

            assertThatThrownBy(
                    () -> userValidator.validateSignUp("user@example.com")
            ).isInstanceOfSatisfying(
                    DomainException.class,
                    exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(UserErrorCode.DUPLICATE_EMAIL)
            );
        }
    }

    @Nested
    @DisplayName("로그인 비밀번호 검증")
    class ValidateLoginPassword {

        @Test
        @DisplayName("원문 비밀번호와 암호화 비밀번호가 일치하면 통과한다")
        void acceptsMatchingPassword() {
            given(passwordEncoder.matches("Password1!", "encoded-password"))
                    .willReturn(true);

            assertThatCode(
                    () -> userValidator.validateLoginPassword(
                            "Password1!",
                            "encoded-password"
                    )
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("비밀번호가 일치하지 않으면 로그인 실패 예외가 발생한다")
        void rejectsMismatchedPassword() {
            given(passwordEncoder.matches("WrongPassword1!", "encoded-password"))
                    .willReturn(false);

            assertThatThrownBy(
                    () -> userValidator.validateLoginPassword(
                            "WrongPassword1!",
                            "encoded-password"
                    )
            ).isInstanceOfSatisfying(
                    DomainException.class,
                    exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_LOGIN_CREDENTIALS)
            );
        }
    }
}
