package org.teamsai.saibackend.domain.user.service;


import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public void validateSignUp(String email) {
        validateDuplicateEmail(email);
    }

    public void validateLoginPassword(
            String rawPassword,
            String encodedPassword
    ) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw UserErrorCode.INVALID_LOGIN_CREDENTIALS.toException();
        }
    }

    private void validateDuplicateEmail(String email) {
        if (userMapper.existsByEmail(email)) {
            throw UserErrorCode.DUPLICATE_EMAIL.toException();
        }
    }


}
