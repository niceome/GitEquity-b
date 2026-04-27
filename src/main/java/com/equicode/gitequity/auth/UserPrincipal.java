package com.equicode.gitequity.auth;

import com.equicode.gitequity.domain.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// JWT 인증 이후 SecurityContext에 저장되는 인증 주체
// @AuthenticationPrincipal UserPrincipal 로 컨트롤러에서 주입받음
@Getter
@RequiredArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getUsername(), user.getEmail());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override public String getPassword() { return null; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
