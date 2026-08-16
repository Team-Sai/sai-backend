package org.teamsai.saibackend.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserLoginDTO {

    private String accessToken;
    private String refreshToken;
    private String userToken;
    private String name;

    public static UserLoginDTO of(
            UserDTO user,
            String accessToken,
            String refreshToken
    ){
        return UserLoginDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userToken(user.getUserToken())
                .name(user.getName())
                .build();
    }
}
