package org.teamsai.saibackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.user.dto.request.UserLoginRequest;
import org.teamsai.saibackend.domain.user.dto.request.UserSignUpRequest;
import org.teamsai.saibackend.domain.user.dto.response.UserLoginResponse;
import org.teamsai.saibackend.domain.user.dto.response.UserSignUpResponse;
import org.teamsai.saibackend.domain.user.entity.User;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.jwt.JwtTokenProvider;

import java.security.SecureRandom;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserMapper userMapper;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public UserSignUpResponse signUp(UserSignUpRequest request) {
        String email = normalizeEmail(request.getEmail());


        userValidator.validateSignUp(email);

        User user = User.builder()
                .userKey(createUserKey())
                .email(email)
                .password(
                        passwordEncoder.encode(request.getPassword())
                )
                .name(request.getName().trim())
                .birthDate(request.getBirthDate())
                .build();

        userMapper.insert(user);

        return UserSignUpResponse.from(user);
    }

    public UserLoginResponse login(UserLoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        User user = userMapper.findByEmail(email)
                .orElseThrow(
                        UserErrorCode.INVALID_LOGIN_CREDENTIALS::toException
                );
        userValidator.validateLoginPassword(
                request.getPassword(),
                user.getPassword()
        );

        String accessToken =
                jwtTokenProvider.createAccessToken(user.getUserKey());

        return UserLoginResponse.of(
                user,
                accessToken
        );
    }

    private static final String USER_KEY_PREFIX = "SAI-";

    private static final String USER_KEY_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int USER_KEY_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();


    private String createUserKey() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder key = new StringBuilder(USER_KEY_PREFIX);

            for (int i = 0; i < USER_KEY_LENGTH; i++) {
                int index = RANDOM.nextInt(USER_KEY_CHARACTERS.length());
                key.append(USER_KEY_CHARACTERS.charAt(index));
            }

            String userKey = key.toString();

            if (!userMapper.existsByUserKey(userKey)) {
                return userKey;
            }
        }

        throw UserErrorCode.USER_KEY_GENERATION_FAILED.toException();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}