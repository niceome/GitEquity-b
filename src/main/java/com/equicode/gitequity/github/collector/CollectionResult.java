package com.equicode.gitequity.github.collector;

public record CollectionResult(
        int commits,
        int pullRequests,
        int reviews,
        int issues
) {
    public int total() {
        return commits + pullRequests + reviews + issues;
    }
}
