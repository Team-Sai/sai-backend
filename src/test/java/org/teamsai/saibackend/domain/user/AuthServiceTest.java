package org.teamsai.saibackend.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saibackend.domain.user.dto.request.UserLoginRequest;
import org.teamsai.saibackend.domain.user.dto.request.UserSignUpRequest;
import org.teamsai.saibackend.domain.user.dto.response.UserLoginResponse;
import org.teamsai.saibackend.domain.user.dto.response.UserSignUpResponse;
import org.teamsai.saibackend.domain.user.entity.User;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.domain.user.service.AuthService;
import org.teamsai.saibackend.domain.user.service.UserValidator;
import org.teamsai.saibackend.global.exception.DomainException;
import org.teamsai.saibackend.global.jwt.JwtTokenProvider;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    private static final String USER_KEY = "SAI-ABCDEFGH";
    private static final String RAW_PASSWORD = "Password1!";
    private static final String ENCODED_PASSWORD = "encoded-password";

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserValidator userValidator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("회원가입")
    class SignUp {

        @Test
        @DisplayName("이메일과 이름을 정리하고 비밀번호를 암호화해 회원을 저장한다")
        void signUpSuccess() {
            UserSignUpRequest request = createSignUpRequest(
                    "  USER@Example.COM  ",
                    RAW_PASSWORD,
                    "  김사이  ",
                    LocalDate.of(2002, 10, 22)
            );

            given(userMapper.existsByUserKey(anyString())).willReturn(false);
            given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userMapper.insert(any(User.class))).willReturn(1);

            UserSignUpResponse response = authService.signUp(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

            verify(userValidator).validateSignUp("user@example.com");
            verify(passwordEncoder).encode(RAW_PASSWORD);
            verify(userMapper).insert(userCaptor.capture());

            User savedUser = userCaptor.getValue();

            assertThat(savedUser.getUserKey())
                    .matches("^SAI-[A-HJ-NP-Z2-9]{8}$");
            assertThat(savedUser.getEmail()).isEqualTo("user@example.com");
            assertThat(savedUser.getPassword()).isEqualTo(ENCODED_PASSWORD);
            assertThat(savedUser.getName()).isEqualTo("김사이");
            assertThat(savedUser.getBirthDate())
                    .isEqualTo(LocalDate.of(2002, 10, 22));

            assertThat(response.getUserKey()).isEqualTo(savedUser.getUserKey());
            assertThat(response.getEmail()).isEqualTo("user@example.com");
            assertThat(response.getName()).isEqualTo("김사이");
        }

        @Test
        @DisplayName("회원가입 검증에 실패하면 암호화와 저장을 수행하지 않는다")
        void signUpStopsWhenValidationFails() {
            UserSignUpRequest request = createSignUpRequest(
                    "duplicate@example.com",
                    RAW_PASSWORD,
                    "김사이",
                    LocalDate.of(2002, 10, 22)
            );
            DomainException expectedException =
                    UserErrorCode.DUPLICATE_EMAIL.toException();

            doThrow(expectedException)
                    .when(userValidator)
                    .validateSignUp("duplicate@example.com");

            assertThatThrownBy(() -> authService.signUp(request))
                    .isSameAs(expectedException);

            verify(passwordEncoder, never()).encode(anyString());
            verify(userMapper, never()).existsByUserKey(anyString());
            verify(userMapper, never()).insert(any(User.class));
        }

        @Test
        @DisplayName("사용자 키를 10회 연속 생성하지 못하면 예외가 발생한다")
        void signUpFailsWhenUserKeyGenerationFails() {
            UserSignUpRequest request = createSignUpRequest(
                    "user@example.com",
                    RAW_PASSWORD,
                    "김사이",
                    LocalDate.of(2002, 10, 22)
            );

            given(userMapper.existsByUserKey(anyString())).willReturn(true);

            assertThatThrownBy(() -> authService.signUp(request))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(UserErrorCode.USER_KEY_GENERATION_FAILED)
                    );

            verify(userMapper, times(10)).existsByUserKey(anyString());
            verify(passwordEncoder, never()).encode(anyString());
            verify(userMapper, never()).insert(any(User.class));
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("회원 조회와 비밀번호 검증에 성공하면 JWT를 발급한다")
        void loginSuccess() {
            UserLoginRequest request = createLoginRequest(
                    "  USER@Example.COM  ",
                    RAW_PASSWORD
            );
            User user = createUser();

            given(userMapper.findByEmail("user@example.com"))
                    .willReturn(Optional.of(user));
            given(jwtTokenProvider.createAccessToken(USER_KEY))
                    .willReturn("access-token");

            UserLoginResponse response = authService.login(request);

            verify(userValidator).validateLoginPassword(
                    RAW_PASSWORD,
                    ENCODED_PASSWORD
            );
            verify(jwtTokenProvider).createAccessToken(USER_KEY);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getUserKey()).isEqualTo(USER_KEY);
            assertThat(response.getName()).isEqualTo("김사이");
        }

        @Test
        @DisplayName("이메일에 해당하는 회원이 없으면 로그인에 실패한다")
        void loginFailsWhenUserDoesNotExist() {
            UserLoginRequest request = createLoginRequest(
                    "missing@example.com",
                    RAW_PASSWORD
            );

            given(userMapper.findByEmail("missing@example.com"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(UserErrorCode.INVALID_LOGIN_CREDENTIALS)
                    );

            verify(userValidator, never())
                    .validateLoginPassword(anyString(), anyString());
            verify(jwtTokenProvider, never())
                    .createAccessToken(anyString());
        }

        @Test
        @DisplayName("비밀번호 검증에 실패하면 JWT를 발급하지 않는다")
        void loginFailsWhenPasswordDoesNotMatch() {
            UserLoginRequest request = createLoginRequest(
                    "user@example.com",
                    "WrongPassword1!"
            );
            User user = createUser();
            DomainException expectedException =
                    UserErrorCode.INVALID_LOGIN_CREDENTIALS.toException();

            given(userMapper.findByEmail("user@example.com"))
                    .willReturn(Optional.of(user));
            doThrow(expectedException)
                    .when(userValidator)
                    .validateLoginPassword(
                            "WrongPassword1!",
                            ENCODED_PASSWORD
                    );

            assertThatThrownBy(() -> authService.login(request))
                    .isSameAs(expectedException);

            verify(jwtTokenProvider, never())
                    .createAccessToken(anyString());
        }
    }

    private UserSignUpRequest createSignUpRequest(
            String email,
            String password,
            String name,
            LocalDate birthDate
    ) {
        UserSignUpRequest request = new UserSignUpRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "birthDate", birthDate);
        return request;
    }

    private UserLoginRequest createLoginRequest(
            String email,
            String password
    ) {
        UserLoginRequest request = new UserLoginRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }

    private User createUser() {
        return User.builder()
                .userId(1L)
                .userKey(USER_KEY)
                .email("user@example.com")
                .password(ENCODED_PASSWORD)
                .name("김사이")
                .birthDate(LocalDate.of(2002, 10, 22))
                .build();
    }
}
