package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 이슈/PR 코멘트 "실질성"(S6, 커뮤니케이션 기여) — Hundhausen et al. (2023)
// score = CommentScorer가 코멘트 본문 길이로 산출한 raw 점수 (사용자별 cap 적용 전)
@Getter
@Entity
@Table(name = "comment_contributions",
        indexes = {
                @Index(name = "idx_comment_contributions_project_author", columnList = "project_id, author_github_id"),
                @Index(name = "idx_comment_contributions_project_comment_id",
                        columnList = "project_id, comment_id", unique = true)
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CommentContribution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // 코멘트 작성자의 GitHub 사용자 ID (User.githubId와 매칭하여 점수 계산에 사용)
    @Column(name = "author_github_id", nullable = false)
    private Long authorGithubId;

    // 코멘트가 달린 이슈 또는 PR 번호 (issue/PR 번호 namespace 공유)
    @Column(name = "issue_number", nullable = false)
    private Integer issueNumber;

    // GitHub 코멘트 ID — 중복 수집 방지
    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "content_length", nullable = false)
    private int contentLength;

    @Column(name = "has_code_block", nullable = false)
    private boolean hasCodeBlock;

    // CommentScorer 산출 raw 점수 (사용자별 합산 후 CommunicationCapCalculator로 cap 적용)
    @Column(nullable = false)
    private double score;

    @Column(name = "created_at_github")
    private LocalDateTime createdAtGithub;
}
