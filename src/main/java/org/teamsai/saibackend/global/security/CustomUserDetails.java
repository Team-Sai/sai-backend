package org.teamsai.saibackend.global.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.teamsai.saibackend.domain.user.dto.UserDTO;

import java.util.Collection;
import java.util.Collections;

@Getter
public class CustomUserDetails implements UserDetails {

    private final String userToken;
    private final String userKey;
    private final Long userId;

    public CustomUserDetails(UserDTO user) {
        this.userToken = user.getUserToken();
        this.userKey = user.getUserKey();
        this.userId = user.getUserId();
    }

    @Override
    public String getUsername() {
        return userToken;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
