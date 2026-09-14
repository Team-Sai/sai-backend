package org.teamsai.saibackend.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.user.dto.UserDTO;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSignUpResponse {

    private String userToken;
    private String email;
    private String name;

    public static UserSignUpResponse from(UserDTO user) {
        return new UserSignUpResponse(
                user.getUserToken(),
                user.getEmail(),
                user.getName()
        );
    }
}
