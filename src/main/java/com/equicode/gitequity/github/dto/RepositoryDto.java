package com.equicode.gitequity.github.dto;

public record RepositoryDto(
        Long id,
        String fullName,
        String description,
        Boolean privateRepo
) {}
