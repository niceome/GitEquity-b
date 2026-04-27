package com.equicode.gitequity.repository;

import com.equicode.gitequity.domain.Contribution;
import com.equicode.gitequity.domain.ContributionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    List<Contribution> findByProjectIdAndUserId(Long projectId, Long userId);

    List<Contribution> findByProjectId(Long projectId);

    boolean existsByProjectIdAndTypeAndGithubId(Long projectId, ContributionType type, String githubId);

    List<Contribution> findByProjectIdAndType(Long projectId, ContributionType type);

    @Query("SELECT c.user, SUM(c.rawScore) FROM Contribution c WHERE c.project.id = :projectId GROUP BY c.user")
    List<Object[]> findRawScoreSumGroupByUser(@Param("projectId") Long projectId);
}
