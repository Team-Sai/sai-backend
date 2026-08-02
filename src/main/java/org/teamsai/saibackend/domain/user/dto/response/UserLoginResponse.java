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
public class UserLoginResponse {

    private String accessToken;
    private String userToken;
    private String name;

    public static UserLoginResponse of(
            UserDTO user,
            String accessToken
    ) {
        return UserLoginResponse.builder()
                .accessToken(accessToken)
                .userToken(user.getUserToken())
                .name(user.getName())
                .build();
    }
}