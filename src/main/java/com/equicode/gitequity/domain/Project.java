package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

// GitHub 레포지토리와 연결된 프로젝트
// weight_config: 프로젝트별 기여 유형 가중치 오버라이드 (JSONB)
// 예: {"COMMIT": 1.5, "PR": 3.0, "REVIEW": 0.5, "ISSUE": 0.5}
@Getter
@Entity
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 프로젝트 소유자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    // GitHub 레포 소유자 (org 또는 개인 계정명)
    @Column(nullable = false)
    private String repoOwner;

    // GitHub 레포 이름
    @Column(nullable = false)
    private String repoName;

    // GitHub 레포 URL
    private String repoUrl;

    // 프로젝트별 가중치 설정 (null이면 기본값 사용)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Double> weightConfig;

    public void updateWeightConfig(Map<String, Double> weightConfig) {
        this.weightConfig = weightConfig;
    }
}
