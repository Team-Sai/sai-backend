package org.teamsai.saibackend.domain.user.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    USER_NOT_FOUND("404","유저를 찾을 수 없음");
    private final String code;
            private final String message;
}
