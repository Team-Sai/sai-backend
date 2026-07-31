package org.teamsai.saibackend.domain.user.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.teamsai.saibackend.global.common.BaseEntity;

import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {
    Long userId;
    String userKey;
    String email;
    String password;
    String name;
    Date birthDate;
}
