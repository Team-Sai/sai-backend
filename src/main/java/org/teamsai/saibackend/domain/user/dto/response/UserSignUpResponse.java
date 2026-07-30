package org.teamsai.saibackend.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.teamsai.saibackend.domain.user.entity.User;

@Getter
@AllArgsConstructor
public class UserSignUpResponse {

    private String userKey;
    private String email;
    private String name;

    public static UserSignUpResponse from(User user) {
        return new UserSignUpResponse(
                user.getUserKey(),
                user.getEmail(),
                user.getName()
        );
    }
}
