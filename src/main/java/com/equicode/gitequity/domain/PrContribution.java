package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

// PR 단위로 수집한 "최종 diff 실질 변경량" 기여
// net_lines / density는 DensityCalculator로 patch를 분석해 수집 시점에 채워진다
// (net_lines = 실질 변경 라인 수, density = net_lines / gross 변경 라인 수)
// pr_type은 PrTypeClassifier로 수집 시점에 채워진다 (PrType.name())
// dmm_complexity는 DmmCollectorService가 dmm_analyzer.py 결과를 PR 번호로 조인하여 채운다
@Getter
@Entity
@Table(name = "pr_contributions",
        indexes = {
                @Index(name = "idx_pr_contributions_project", columnList = "project_id"),
                @Index(name = "idx_pr_contributions_project_pr_number",
                        columnList = "project_id, pr_number", unique = true)
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PrContribution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // PR 작성자의 GitHub 사용자 ID (User.githubId와 매칭하여 점수 계산에 사용)
    @Column(name = "author_github_id", nullable = false)
    private Long authorGithubId;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(nullable = false)
    private String title;

    // 라벨 이름 목록 (예: ["bug", "enhancement"])
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> labels;

    // true = 병합된 "정식 기여", false = closed-unmerged "탐색 기여"
    @Column(name = "is_merged", nullable = false)
    private boolean isMerged;

    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    @Column(nullable = false)
    private int additions;

    @Column(nullable = false)
    private int deletions;

    // 실질 변경 라인 수 (DensityCalculator 결과, 공백/주석/import/괄호 등 비실질 라인 제외)
    @Column(name = "net_lines", nullable = false)
    private int netLines;

    // source code density = net_lines / gross 변경 라인 수 (DensityCalculator 결과)
    private Double density;

    // PrTypeClassifier가 분류한 PrType.name() (예: "FEAT", "FIX", "UNKNOWN")
    @Column(name = "pr_type")
    private String prType;

    // DMM 기반 복잡도 점수 (di Biase et al. 2019, dmm_unit_size/complexity/interfacing 평균)
    @Column(name = "dmm_complexity")
    private Double dmmComplexity;

    // squash 커밋 메시지의 Co-authored-by 트레일러로 추출한 공동 작성자 User.githubId 목록 (S12)
    // 비어있으면 작성자(authorGithubId) 단독 기여로 처리
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "co_author_github_ids", columnDefinition = "jsonb")
    private List<Long> coAuthorGithubIds;

    // DmmCollectorService가 dmm_analyzer.py 분석 결과로 갱신
    public void applyDmmComplexity(Double dmmComplexity) {
        this.dmmComplexity = dmmComplexity;
    }

    // CoAuthorAttributionCollectorService가 Co-authored-by 트레일러 파싱 결과로 갱신 (S12)
    public void applyCoAuthors(List<Long> coAuthorGithubIds) {
        this.coAuthorGithubIds = coAuthorGithubIds;
    }
}
