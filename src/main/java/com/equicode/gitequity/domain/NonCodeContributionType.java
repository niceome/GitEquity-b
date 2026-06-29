package com.equicode.gitequity.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NonCodeContributionType {
    MENTORING(1.0),
    DESIGN(1.0),
    DEBUG_HELP(0.8),
    DOCS_OPS(0.6);

    private final double weight;
}
