package com.equicode.gitequity.github.collector;

public record CollectionResult(
        int commits,
        int pullRequests,
        int reviews,
        int issues,
        int prContributions
) {
    public int total() {
        return commits + pullRequests + reviews + issues + prContributions;
    }
}
