package org.teamsai.saibackend.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            DomainException exception
    ) {
        HttpStatus httpStatus = exception.getHttpStatus();

        if (httpStatus.is5xxServerError()) {
            log.error(
                    "도메인 예외 발생: status={}, message={}",
                    httpStatus.value(),
                    exception.getMessage(),
                    exception
            );
        } else {
            log.warn(
                    "도메인 예외 발생: status={}, message={}",
                    httpStatus.value(),
                    exception.getMessage()
            );
        }

        return ResponseEntity
                .status(httpStatus)
                .body(
                        ErrorResponse.of(
                                httpStatus.value(),
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception
    ) {
        log.warn(
                "요청 파라미터 타입 불일치: name={}, value={}, requiredType={}",
                exception.getName(),
                exception.getValue(),
                exception.getRequiredType() != null
                        ? exception.getRequiredType().getSimpleName()
                        : "unknown"
        );

        String message = String.format(
                "요청 파라미터 '%s'의 값이 올바르지 않습니다.",
                exception.getName()
        );

        return ResponseEntity
                .badRequest()
                .body(
                        ErrorResponse.of(
                                HttpStatus.BAD_REQUEST.value(),
                                message
                        )
                );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");

        return ResponseEntity
                .badRequest()
                .body(
                        ErrorResponse.of(
                                HttpStatus.BAD_REQUEST.value(),
                                message
                        )
                );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(
                        ErrorResponse.of(
                                HttpStatus.BAD_REQUEST.value(),
                                "요청 데이터 형식이 올바르지 않습니다."
                        )
                );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgumentException(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        if (isApiRequest(request)) {
            return ResponseEntity
                    .badRequest()
                    .body(
                            ErrorResponse.of(
                                    HttpStatus.BAD_REQUEST.value(),
                                    exception.getMessage()
                            )
                    );
        }

        ModelAndView modelAndView =
                new ModelAndView("error/400");

        modelAndView.setStatus(HttpStatus.BAD_REQUEST);
        modelAndView.addObject(
                "message",
                exception.getMessage()
        );

        return modelAndView;
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "처리되지 않은 예외가 발생했습니다.",
                exception
        );

        String message =
                "요청을 처리하는 중 오류가 발생했습니다.";

        if (isApiRequest(request)) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            ErrorResponse.of(
                                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                    message
                            )
                    );
        }

        ModelAndView modelAndView =
                new ModelAndView("error/500");

        modelAndView.setStatus(
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        modelAndView.addObject(
                "message",
                message
        );

        return modelAndView;
    }

    private boolean isApiRequest(
            HttpServletRequest request
    ) {
        return request.getRequestURI()
                .startsWith("/api/");
    }
}
