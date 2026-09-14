package org.teamsai.saibackend.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String userToken;
    private String email;
    private String name;
    private LocalDate birthDate;
    private LocalDateTime createdAt;

    public static UserResponse from(UserDTO user) {
        return UserResponse.builder()
                .userToken(user.getUserToken())
                .email(user.getEmail())
                .name(user.getName())
                .birthDate(user.getBirthDate())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
