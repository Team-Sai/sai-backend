package org.teamsai.saibackend.global.jwt;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        UserErrorCode errorCode =
                UserErrorCode.UNAUTHORIZED;

        response.setStatus(
                errorCode.getHttpStatus().value()
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        String responseBody = String.format(
                "{\"status\":%d,\"message\":\"%s\"}",
                errorCode.getHttpStatus().value(),
                errorCode.getMessage()
        );

        response.getWriter().write(responseBody);
    }
}