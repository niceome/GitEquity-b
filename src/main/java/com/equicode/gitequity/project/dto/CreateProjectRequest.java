package com.equicode.gitequity.project.dto;

public record CreateProjectRequest(
        String name,
        String repoOwner,
        String repoName
) {}
