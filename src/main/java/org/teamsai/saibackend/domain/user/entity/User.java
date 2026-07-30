package org.teamsai.saibackend.domain.user.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.global.common.BaseEntity;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {
    private Long userId;
    private String userKey;
    private String email;
    private String password;
    private String name;
    private LocalDate birthDate;

}
