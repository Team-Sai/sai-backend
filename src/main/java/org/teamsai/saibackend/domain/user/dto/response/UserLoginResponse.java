package org.teamsai.saibackend.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.teamsai.saibackend.domain.user.entity.User;

@Getter
@Builder
public class UserLoginResponse {

    private String accessToken;
    private String userKey;
    private String name;

    public static UserLoginResponse of(
            User user,
            String accessToken
    ) {
        return UserLoginResponse.builder()
                .accessToken(accessToken)
                .userKey(user.getUserKey())
                .name(user.getName())
                .build();
    }
}