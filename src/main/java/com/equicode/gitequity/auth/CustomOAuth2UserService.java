package com.equicode.gitequity.auth;

import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// GitHub에서 받아온 사용자 정보로 DB User를 생성 또는 업데이트
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        Long githubId = ((Number) attributes.get("id")).longValue();
        String username = (String) attributes.get("login");
        // GitHub 이메일은 사용자가 비공개 설정 시 null일 수 있음
        String email = (String) attributes.get("email");
        String avatarUrl = (String) attributes.get("avatar_url");
        String accessToken = request.getAccessToken().getTokenValue();

        User user = userRepository.findByGithubId(githubId)
                .map(existing -> {
                    existing.update(username, email, avatarUrl, accessToken);
                    return existing;
                })
                .orElseGet(() -> User.builder()
                        .githubId(githubId)
                        .username(username)
                        .email(email)
                        .avatarUrl(avatarUrl)
                        .accessToken(accessToken)
                        .build());

        User saved = userRepository.save(user);

        return new CustomOAuth2User(
                attributes,
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                username,
                saved.getId()
        );
    }
}
