package org.teamsai.saibackend.global.exception;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode<E extends RuntimeException> {

    HttpStatus getHttpStatus();

    String getMessage();

    E toException();
}
