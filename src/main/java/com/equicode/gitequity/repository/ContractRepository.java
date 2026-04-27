package com.equicode.gitequity.repository;

import com.equicode.gitequity.domain.Contract;
import com.equicode.gitequity.domain.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByProjectId(Long projectId);

    List<Contract> findByProjectIdAndStatus(Long projectId, ContractStatus status);
}
