package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 프로젝트 멤버십 — 유저와 프로젝트의 N:M 관계를 해소하는 중간 테이블
@Getter
@Entity
@Table(name = "project_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_project_members_project_user",
                columnNames = {"project_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // OWNER: 프로젝트 생성자, MEMBER: 기여자, VIEWER: 조회만 가능
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Column(nullable = false)
    private LocalDateTime joinedAt;
}
