package org.teamsai.saibackend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DomainException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final BaseErrorCode<?> errorCode;

    public DomainException(
            HttpStatus httpStatus,
            BaseErrorCode<?> errorCode
    ) {
        super(errorCode.getMessage());
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}