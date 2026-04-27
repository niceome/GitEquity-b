package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;

// GitHub OAuth 로그인 사용자 정보 저장
// access_token은 GitHub API 호출 시 사용
@Getter
@Entity
@Table(name = "users",
        indexes = @Index(name = "idx_users_github_id", columnList = "github_id", unique = true))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // GitHub 계정 고유 식별자 (GitHub에서 발급)
    @Column(name = "github_id", unique = true, nullable = false)
    private Long githubId;

    // GitHub 로그인 아이디 (@ 없는 username)
    @Column(nullable = false)
    private String username;

    private String email;

    private String avatarUrl;

    // GitHub OAuth 액세스 토큰 (API 호출 및 갱신에 사용)
    private String accessToken;

    // 재로그인 시 최신 정보로 갱신
    public void update(String username, String email, String avatarUrl, String accessToken) {
        this.username = username;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.accessToken = accessToken;
    }
}
