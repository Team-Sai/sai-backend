package org.teamsai.saibackend.domain.user.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long userId;
    private String userToken; //회원가입할 때 발급
    private String userKey; //계좌 연결할 때 발급
    private String email;
    private String password;
    private String name;
    private LocalDate birthDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
