package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 리뷰 "실질성"(S5) 기여 — McIntosh et al. (2014)
// score = ReviewSubstanceScorer가 리뷰 상태(state) + 본문/인라인 코멘트 총 길이로 산출
@Getter
@Entity
@Table(name = "review_contributions",
        indexes = {
                @Index(name = "idx_review_contributions_project_reviewer", columnList = "project_id, reviewer_github_id"),
                @Index(name = "idx_review_contributions_project_review_id",
                        columnList = "project_id, review_id", unique = true)
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReviewContribution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // 리뷰어의 GitHub 사용자 ID (User.githubId와 매칭하여 점수 계산에 사용)
    @Column(name = "reviewer_github_id", nullable = false)
    private Long reviewerGithubId;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    // GitHub 리뷰 ID — 중복 수집 방지
    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    // "APPROVED" | "CHANGES_REQUESTED" | "COMMENTED" | "DISMISSED" 등
    @Column(nullable = false)
    private String state;

    // 리뷰 본문 + 인라인 코멘트를 합친 텍스트 길이 (substance 계산에 사용된 값)
    @Column(name = "content_length", nullable = false)
    private int contentLength;

    @Column(name = "has_code_block", nullable = false)
    private boolean hasCodeBlock;

    // ReviewSubstanceScorer 산출 점수 (base × substance + codeBlock bonus)
    @Column(nullable = false)
    private double score;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
}
