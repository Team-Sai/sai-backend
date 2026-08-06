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
    ),

    CANNOT_SELECT_SELF(
            HttpStatus.BAD_REQUEST,
        "본인은 요청 대상으로 선택할 수 없습니다."
    ),
    ALREADY_LINKED_USER(
            HttpStatus.ALREADY_REPORTED,
            "이미 연동된 회원입니다."
    ),
    INVALID_LINK_STATE(
            HttpStatus.BAD_REQUEST,
            "유효하지 않거나 만료된 연동 요청입니다. 다시 시도해주세요."
    ),
    LINK_KEY_UPDATE_CONFLICT(
            HttpStatus.CONFLICT,
            "계좌 연동 처리 중 충돌이 발생했습니다. 다시 시도해주세요."

    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}