package com.movie.ticket.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/** Spring Security 用户身份适配，封装用户 ID、角色、影院归属和授权信息。 */
public class UserDetailsImpl implements UserDetails {

    @Getter
    private final Long id;
    private final String username;
    @Getter
    private final String role;
    @Getter
    private final Long cinemaId;

    public UserDetailsImpl(Long id, String username, String role) {
        this(id, username, role, null);
    }

    public UserDetailsImpl(Long id, String username, String role, Long cinemaId) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.cinemaId = cinemaId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
