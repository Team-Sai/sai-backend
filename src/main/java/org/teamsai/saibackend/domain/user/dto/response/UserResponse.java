package org.teamsai.saibackend.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.teamsai.saibackend.domain.user.entity.User;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class UserResponse {

    private String userKey;
    private String email;
    private String name;
    private LocalDate birthDate;

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserKey(),
                user.getEmail(),
                user.getName(),
                user.getBirthDate()
        );
    }
}
