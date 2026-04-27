package com.equicode.gitequity.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContributionType {
    COMMIT(1.0),
    PR(3.0),
    REVIEW(0.5),
    ISSUE(0.5);

    private final double weight;
}
