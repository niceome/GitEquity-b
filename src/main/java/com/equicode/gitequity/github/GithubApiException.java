package com.equicode.gitequity.github;

public class GithubApiException extends RuntimeException {

    private final int statusCode;

    public GithubApiException(int statusCode, String body) {
        super("GitHub API error %d: %s".formatted(statusCode, body));
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
