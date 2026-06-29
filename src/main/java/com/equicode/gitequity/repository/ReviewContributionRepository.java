package com.equicode.gitequity.repository;

import com.equicode.gitequity.domain.ReviewContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewContributionRepository extends JpaRepository<ReviewContribution, Long> {

    boolean existsByProjectIdAndReviewId(Long projectId, Long reviewId);

    List<ReviewContribution> findByProjectId(Long projectId);

    // 사용자별 S5 리뷰 실질성 점수 합 — EquityCalculatorService "리뷰점수합" 산출에 사용
    @Query("SELECT r.reviewerGithubId, SUM(r.score) FROM ReviewContribution r " +
            "WHERE r.project.id = :projectId GROUP BY r.reviewerGithubId")
    List<Object[]> sumScoreGroupByReviewer(@Param("projectId") Long projectId);

    // 리뷰가 1개 이상 달린 PR 번호 목록 — unmerged PR "탐색 기여" 인정 조건 판단에 사용
    @Query("SELECT DISTINCT r.prNumber FROM ReviewContribution r WHERE r.project.id = :projectId")
    List<Integer> findDistinctPrNumbersByProjectId(@Param("projectId") Long projectId);
}
