package org.teamsai.saibackend.domain.user;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.dto.request.UserLoginRequest;
import org.teamsai.saibackend.domain.user.dto.request.UserSignUpRequest;
import org.teamsai.saibackend.domain.user.dto.response.UserLoginResponse;
import org.teamsai.saibackend.domain.user.dto.response.UserSignUpResponse;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.domain.user.service.AuthService;
import org.teamsai.saibackend.domain.user.service.AuthValidator;
import org.teamsai.saibackend.global.exception.DomainException;
import org.teamsai.saibackend.global.jwt.JwtTokenProvider;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

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

    private static final String USER_TOKEN = "SAI-ABCDEFGH";
    private static final String RAW_PASSWORD = "Password1!";
    private static final String ENCODED_PASSWORD = "encoded-password";

    private final Validator beanValidator =
            Validation.buildDefaultValidatorFactory()
                    .getValidator();

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuthValidator authValidator;

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

            given(userMapper.existsByUserToken(anyString()))
                    .willReturn(false);

            given(passwordEncoder.encode(RAW_PASSWORD))
                    .willReturn(ENCODED_PASSWORD);

            given(userMapper.insert(any(UserDTO.class)))
                    .willReturn(1);

            UserSignUpResponse response =
                    authService.signUp(request);

            ArgumentCaptor<UserDTO> userCaptor =
                    ArgumentCaptor.forClass(UserDTO.class);

            verify(authValidator)
                    .validateSignUp("user@example.com");

            verify(passwordEncoder)
                    .encode(RAW_PASSWORD);

            verify(userMapper)
                    .insert(userCaptor.capture());

            UserDTO savedUser = userCaptor.getValue();

            assertThat(savedUser.getUserToken())
                    .matches("^SAI-[A-HJ-NP-Z2-9]{8}$");

            assertThat(savedUser.getUserKey())
                    .isNull(); // 계좌 연동 전까지는 userKey가 비어있어야 한다

            assertThat(savedUser.getEmail())
                    .isEqualTo("user@example.com");

            assertThat(savedUser.getPassword())
                    .isEqualTo(ENCODED_PASSWORD);

            assertThat(savedUser.getName())
                    .isEqualTo("김사이");

            assertThat(savedUser.getBirthDate())
                    .isEqualTo(LocalDate.of(2002, 10, 22));

            assertThat(response.getUserToken())
                    .isEqualTo(savedUser.getUserToken());

            assertThat(response.getEmail())
                    .isEqualTo("user@example.com");

            assertThat(response.getName())
                    .isEqualTo("김사이");
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
                    .when(authValidator)
                    .validateSignUp("duplicate@example.com");

            assertThatThrownBy(
                    () -> authService.signUp(request)
            ).isSameAs(expectedException);

            verify(passwordEncoder, never())
                    .encode(anyString());

            verify(userMapper, never())
                    .existsByUserToken(anyString());

            verify(userMapper, never())
                    .insert(any(UserDTO.class));
        }

        @Test
        @DisplayName("사용자 토큰을 10회 연속 생성하지 못하면 예외가 발생한다")
        void signUpFailsWhenUserTokenGenerationFails() {
            UserSignUpRequest request = createSignUpRequest(
                    "user@example.com",
                    RAW_PASSWORD,
                    "김사이",
                    LocalDate.of(2002, 10, 22)
            );

            given(userMapper.existsByUserToken(anyString()))
                    .willReturn(true);

            assertThatThrownBy(
                    () -> authService.signUp(request)
            ).isInstanceOfSatisfying(
                    DomainException.class,
                    exception -> assertThat(
                            exception.getErrorCode()
                    ).isEqualTo(
                            UserErrorCode.USER_TOKEN_GENERATION_FAILED
                    )
            );

            verify(userMapper, times(10))
                    .existsByUserToken(anyString());

            verify(passwordEncoder, never())
                    .encode(anyString());

            verify(userMapper, never())
                    .insert(any(UserDTO.class));
        }

        @Test
        @DisplayName("저장 시 이메일 유니크 제약을 위반하면 중복 이메일 예외가 발생한다")
        void signUpDuplicateEmailAtInsert() {
            UserSignUpRequest request = createSignUpRequest(
                    "duplicate@example.com",
                    RAW_PASSWORD,
                    "김사이",
                    LocalDate.of(2002, 10, 22)
            );

            given(userMapper.existsByUserToken(anyString()))
                    .willReturn(false);

            given(passwordEncoder.encode(RAW_PASSWORD))
                    .willReturn(ENCODED_PASSWORD);

            given(userMapper.insert(any(UserDTO.class)))
                    .willThrow(
                            new DataIntegrityViolationException(
                                    "Duplicate entry for key 'uk_users_email'"
                            )
                    );

            assertThatThrownBy(
                    () -> authService.signUp(request)
            ).isInstanceOfSatisfying(
                    DomainException.class,
                    exception -> {
                        assertThat(exception.getHttpStatus())
                                .isEqualTo(HttpStatus.CONFLICT);

                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        UserErrorCode.DUPLICATE_EMAIL
                                );

                        assertThat(exception.getMessage())
                                .isEqualTo(
                                        UserErrorCode
                                                .DUPLICATE_EMAIL
                                                .getMessage()
                                );
                    }
            );

            verify(authValidator)
                    .validateSignUp("duplicate@example.com");

            verify(passwordEncoder)
                    .encode(RAW_PASSWORD);

            verify(userMapper)
                    .insert(any(UserDTO.class));

            verify(jwtTokenProvider, never())
                    .createAccessToken(anyString());
        }
    }

    @Nested
    @DisplayName("회원가입 요청값 검증")
    class SignUpRequestValidation {

        @Test
        @DisplayName("미래 날짜를 생년월일로 입력하면 검증에 실패한다")
        void futureBirthDateIsInvalid() {
            UserSignUpRequest request = createSignUpRequest(
                    "user@example.com",
                    RAW_PASSWORD,
                    "김사이",
                    LocalDate.now().plusDays(1)
            );

            Set<ConstraintViolation<UserSignUpRequest>> violations =
                    beanValidator.validate(request);

            assertThat(violations)
                    .extracting(ConstraintViolation::getMessage)
                    .contains(
                            "생년월일은 과거 날짜여야 합니다."
                    );
        }

        @Test
        @DisplayName("과거 날짜를 생년월일로 입력하면 검증을 통과한다")
        void pastBirthDateIsValid() {
            UserSignUpRequest request = createSignUpRequest(
                    "user@example.com",
                    RAW_PASSWORD,
                    "김사이",
                    LocalDate.of(2002, 10, 22)
            );

            Set<ConstraintViolation<UserSignUpRequest>> violations =
                    beanValidator.validate(request);

            assertThat(violations).isEmpty();
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

            UserDTO user = createUser();

            given(userMapper.findByEmail("user@example.com"))
                    .willReturn(Optional.of(user));

            given(jwtTokenProvider.createAccessToken(USER_TOKEN))
                    .willReturn("access-token");

            UserLoginResponse response =
                    authService.login(request);

            verify(authValidator)
                    .validateLoginPassword(
                            RAW_PASSWORD,
                            ENCODED_PASSWORD
                    );

            verify(jwtTokenProvider)
                    .createAccessToken(USER_TOKEN);

            assertThat(response.getAccessToken())
                    .isEqualTo("access-token");

            assertThat(response.getUserToken())
                    .isEqualTo(USER_TOKEN);

            assertThat(response.getName())
                    .isEqualTo("김사이");
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

            assertThatThrownBy(
                    () -> authService.login(request)
            ).isInstanceOfSatisfying(
                    DomainException.class,
                    exception -> assertThat(
                            exception.getErrorCode()
                    ).isEqualTo(
                            UserErrorCode.INVALID_LOGIN_CREDENTIALS
                    )
            );

            verify(authValidator, never())
                    .validateLoginPassword(
                            anyString(),
                            anyString()
                    );

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

            UserDTO user = createUser();

            DomainException expectedException =
                    UserErrorCode
                            .INVALID_LOGIN_CREDENTIALS
                            .toException();

            given(userMapper.findByEmail("user@example.com"))
                    .willReturn(Optional.of(user));

            doThrow(expectedException)
                    .when(authValidator)
                    .validateLoginPassword(
                            "WrongPassword1!",
                            ENCODED_PASSWORD
                    );

            assertThatThrownBy(
                    () -> authService.login(request)
            ).isSameAs(expectedException);

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
        UserSignUpRequest request =
                new UserSignUpRequest();

        ReflectionTestUtils.setField(
                request,
                "email",
                email
        );

        ReflectionTestUtils.setField(
                request,
                "password",
                password
        );

        ReflectionTestUtils.setField(
                request,
                "name",
                name
        );

        ReflectionTestUtils.setField(
                request,
                "birthDate",
                birthDate
        );

        return request;
    }

    private UserLoginRequest createLoginRequest(
            String email,
            String password
    ) {
        UserLoginRequest request =
                new UserLoginRequest();

        ReflectionTestUtils.setField(
                request,
                "email",
                email
        );

        ReflectionTestUtils.setField(
                request,
                "password",
                password
        );

        return request;
    }

    private UserDTO createUser() {
        return UserDTO.builder()
                .userId(1L)
                .userToken(USER_TOKEN)
                .email("user@example.com")
                .password(ENCODED_PASSWORD)
                .name("김사이")
                .birthDate(
                        LocalDate.of(2002, 10, 22)
                )
                .build();
    }
}