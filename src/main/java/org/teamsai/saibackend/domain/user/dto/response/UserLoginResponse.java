package org.teamsai.saibackend.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.user.dto.UserLoginDTO;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginResponse {

    private String accessToken;
    private String userToken;
    private String name;

    public static UserLoginResponse from(
            UserLoginDTO loginDTO
    ) {
        return UserLoginResponse.builder()
                .accessToken(loginDTO.getAccessToken())
                .userToken(loginDTO.getUserToken())
                .name(loginDTO.getName())
                .build();
    }
}