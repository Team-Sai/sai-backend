package org.teamsai.saibackend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(
            IllegalArgumentException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "error/400";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(
            Exception exception,
            Model model
    ) {
        log.error("처리되지 않은 예외가 발생했습니다.", exception);

        model.addAttribute(
                "message",
                "요청을 처리하는 중 오류가 발생했습니다."
        );

        return "error/500";
    }
}