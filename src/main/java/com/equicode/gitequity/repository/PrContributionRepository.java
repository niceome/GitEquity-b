package com.equicode.gitequity.repository;

import com.equicode.gitequity.domain.PrContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrContributionRepository extends JpaRepository<PrContribution, Long> {

    boolean existsByProjectIdAndPrNumber(Long projectId, Integer prNumber);

    Optional<PrContribution> findByProjectIdAndPrNumber(Long projectId, Integer prNumber);

    List<PrContribution> findByProjectId(Long projectId);

    // 사용자별 병합 PR netLines 합 — S6 커뮤니케이션 cap 계산의 "PR 점수 합" 기준
    @Query("SELECT p.authorGithubId, SUM(p.netLines) FROM PrContribution p " +
            "WHERE p.project.id = :projectId AND p.isMerged = true GROUP BY p.authorGithubId")
    List<Object[]> sumNetLinesGroupByAuthor(@Param("projectId") Long projectId);
}
