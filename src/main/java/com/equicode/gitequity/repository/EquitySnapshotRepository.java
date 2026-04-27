package com.equicode.gitequity.repository;

import com.equicode.gitequity.domain.EquitySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EquitySnapshotRepository extends JpaRepository<EquitySnapshot, Long> {

    List<EquitySnapshot> findByProjectIdOrderBySnapshotAtDesc(Long projectId);

    Optional<EquitySnapshot> findTopByProjectIdOrderBySnapshotAtDesc(Long projectId);

    List<EquitySnapshot> findByProjectIdAndSnapshotAt(Long projectId, LocalDateTime snapshotAt);

    List<EquitySnapshot> findByContractId(Long contractId);

    // 프로젝트의 스냅샷 생성 시각 목록 (중복 제거, 최신순)
    @Query("SELECT DISTINCT s.snapshotAt FROM EquitySnapshot s " +
           "WHERE s.project.id = :projectId ORDER BY s.snapshotAt DESC")
    List<LocalDateTime> findDistinctSnapshotAtByProjectId(@Param("projectId") Long projectId);
}
