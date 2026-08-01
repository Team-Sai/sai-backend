package org.teamsai.saibackend.domain.user.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saibackend.global.exception.BaseErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode
        implements BaseErrorCode<DomainException> {

    INVALID_SIGNUP_REQUEST(
            HttpStatus.BAD_REQUEST,
            "회원가입 요청값이 올바르지 않습니다."
    ),

    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "이미 가입된 이메일입니다."
    ),

    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "회원을 찾을 수 없습니다."
    ),

    INVALID_LOGIN_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "로그인이 필요하거나 토큰이 유효하지 않습니다."
    ),

    USER_TOKEN_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
        "회원 초대 코드 생성에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}