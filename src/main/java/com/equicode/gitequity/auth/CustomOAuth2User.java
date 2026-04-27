package com.equicode.gitequity.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

// OAuth2 인증 성공 후 DB에 저장된 userId를 포함하는 래퍼
// OAuth2SuccessHandler에서 userId를 꺼내 JWT를 발급할 때 사용
@Getter
@RequiredArgsConstructor
public class CustomOAuth2User implements OAuth2User {

    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String name;
    private final Long userId;

    @Override
    public Map<String, Object> getAttributes() { return attributes; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getName() { return name; }
}
