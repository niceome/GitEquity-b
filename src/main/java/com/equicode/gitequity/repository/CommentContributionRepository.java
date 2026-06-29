package com.equicode.gitequity.repository;

import com.equicode.gitequity.domain.CommentContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentContributionRepository extends JpaRepository<CommentContribution, Long> {

    boolean existsByProjectIdAndCommentId(Long projectId, Long commentId);

    List<CommentContribution> findByProjectId(Long projectId);

    // 사용자별 S6 raw 점수 합 (cap 적용 전) — CommunicationScoreService에서 사용
    @Query("SELECT c.authorGithubId, SUM(c.score) FROM CommentContribution c " +
            "WHERE c.project.id = :projectId GROUP BY c.authorGithubId")
    List<Object[]> sumRawScoreGroupByAuthor(@Param("projectId") Long projectId);
}
