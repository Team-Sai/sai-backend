package org.teamsai.saibackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.dto.request.UserLoginRequest;
import org.teamsai.saibackend.domain.user.dto.request.UserSignUpRequest;
import org.teamsai.saibackend.domain.user.dto.response.UserLoginResponse;
import org.teamsai.saibackend.domain.user.dto.response.UserSignUpResponse;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.jwt.JwtTokenProvider;

import java.security.SecureRandom;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String USER_TOKEN_PREFIX = "SAI-";

    private static final String USER_TOKEN_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int USER_TOKEN_LENGTH = 8;
    private static final int USER_TOKEN_GENERATION_ATTEMPTS = 10;

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private final UserMapper userMapper;
    private final AuthValidator authValidator;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public UserSignUpResponse signUp(
            UserSignUpRequest request
    ) {
        String email =
                normalizeEmail(request.getEmail());

        authValidator.validateSignUp(email);

        UserDTO user = UserDTO.builder()
                .userToken(createUserToken())
                .userKey(null)
                .email(email)
                .password(
                        passwordEncoder.encode(
                                request.getPassword()
                        )
                )
                .name(request.getName().trim())
                .birthDate(request.getBirthDate())
                .build();

        try {
            userMapper.insert(user);

        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueConstraintViolation(exception)) {
                throw UserErrorCode.DUPLICATE_EMAIL
                        .toException();
            }

            throw exception;
        }

        return UserSignUpResponse.from(user);
    }

    public UserLoginResponse login(
            UserLoginRequest request
    ) {
        String email =
                normalizeEmail(request.getEmail());

        UserDTO user = userMapper.findByEmail(email)
                .orElseThrow(
                        UserErrorCode
                                .INVALID_LOGIN_CREDENTIALS
                                ::toException
                );

        authValidator.validateLoginPassword(
                request.getPassword(),
                user.getPassword()
        );


        String accessToken =
                jwtTokenProvider.createAccessToken(
                        user.getUserId()
                );

        return UserLoginResponse.of(
                user,
                accessToken
        );
    }

    private String createUserToken() {
        for (
                int attempt = 0;
                attempt < USER_TOKEN_GENERATION_ATTEMPTS;
                attempt++
        ) {
            StringBuilder token =
                    new StringBuilder(USER_TOKEN_PREFIX);

            for (int i = 0; i < USER_TOKEN_LENGTH; i++) {
                int index =
                        RANDOM.nextInt(
                                USER_TOKEN_CHARACTERS.length()
                        );

                token.append(
                        USER_TOKEN_CHARACTERS.charAt(index)
                );
            }

            String userToken =
                    token.toString();

            if (!userMapper.existsByUserToken(userToken)) {
                return userToken;
            }
        }

        throw UserErrorCode
                .USER_TOKEN_GENERATION_FAILED
                .toException();
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private boolean isEmailUniqueConstraintViolation(
            Throwable exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            String message =
                    cause.getMessage();

            if (message != null
                    && message.contains("uk_users_email")) {

                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}