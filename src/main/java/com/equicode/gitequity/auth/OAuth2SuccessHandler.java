package com.equicode.gitequity.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// OAuth2 로그인 성공 시 JWT를 발급하고 프론트엔드로 리다이렉트
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        CustomOAuth2User user = (CustomOAuth2User) authentication.getPrincipal();
        String token = jwtTokenProvider.generateToken(user.getUserId());
        response.sendRedirect(redirectUri + "?token=" + token);
    }
}
