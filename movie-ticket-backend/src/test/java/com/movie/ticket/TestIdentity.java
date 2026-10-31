package com.movie.ticket;

import com.movie.ticket.security.UserDetailsImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public final class TestIdentity {
    private TestIdentity() {}
    public static void login(Long id, String role, Long cinemaId) {
        var principal = new UserDetailsImpl(id, "test-" + id, role, cinemaId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    public static void admin() { login(99L, "ADMIN", null); }
    public static void clear() { SecurityContextHolder.clearContext(); }
}
