package com.equicode.gitequity.repository;

import com.equicode.gitequity.domain.ClaimStatus;
import com.equicode.gitequity.domain.NonCodeContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NonCodeContributionRepository extends JpaRepository<NonCodeContribution, Long> {
    List<NonCodeContribution> findByProjectIdAndBeneficiaryIdAndStatus(Long projectId, Long beneficiaryId, ClaimStatus status);
    List<NonCodeContribution> findByProjectIdAndStatus(Long projectId, ClaimStatus status);
}
