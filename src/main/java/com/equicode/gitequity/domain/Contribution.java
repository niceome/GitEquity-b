package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// GitHub에서 수집한 기여 이력 단건
// raw_score = 유형가중치 × count × ccn_score × file_importance
@Getter
@Entity
@Table(name = "contributions",
        indexes = {
                // 프로젝트-유저 기준 조회 (지분 계산 시 주로 사용)
                @Index(name = "idx_contributions_project_user", columnList = "project_id, user_id"),
                // 기여 유형 필터링
                @Index(name = "idx_contributions_project_type", columnList = "project_id, type"),
                // GitHub 중복 수집 방지
                @Index(name = "idx_contributions_github_id", columnList = "github_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Contribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // COMMIT / PR / REVIEW / ISSUE
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContributionType type;

    // GitHub 원본 오브젝트 ID (커밋 SHA, PR 번호 등) — 중복 수집 방지용
    @Column(name = "github_id")
    private String githubId;

    @Column(nullable = false)
    private int count;

    // 순환 복잡도 점수 (Lizard/PMD 측정값)
    @Column(nullable = false)
    private double ccnScore;

    // 파일 중요도 가중치 (경로 기반 + 변경 이력 기반)
    @Column(nullable = false)
    private double fileImportance;

    // 최종 점수: 유형가중치 × count × ccnScore × fileImportance
    @Column(nullable = false)
    private double rawScore;

    // GitHub 기여 발생 시각
    @Column(nullable = false)
    private LocalDateTime occurredAt;

    // CCN 분석 완료 후 점수 반영 (rawScore 재계산 포함)
    public void applyCcnScore(double ccnScore, double typeWeight) {
        this.ccnScore = ccnScore;
        this.rawScore = this.count * ccnScore * this.fileImportance * typeWeight;
    }

    // 파일 중요도 반영 후 rawScore 재계산
    public void applyFileImportance(double fileImportance, double typeWeight) {
        this.fileImportance = fileImportance;
        this.rawScore = this.count * this.ccnScore * fileImportance * typeWeight;
    }
}
