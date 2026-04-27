package com.equicode.gitequity.repository;

import com.equicode.gitequity.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByRepoName(String repoName);

    Optional<Project> findByRepoOwnerAndRepoName(String repoOwner, String repoName);

    List<Project> findByOwnerId(Long ownerId);
}
