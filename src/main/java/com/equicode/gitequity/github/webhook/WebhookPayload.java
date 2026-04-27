package com.equicode.gitequity.github.webhook;

// GitHub 웹훅 공통 페이로드 (push / pull_request 이벤트 공통 필드만 추출)
public record WebhookPayload(
        String action,              // PR 이벤트: "opened", "closed" 등
        PullRequest pullRequest,    // pull_request 이벤트에만 존재
        Repository repository
) {
    public record PullRequest(boolean merged) {}

    public record Repository(String name, Owner owner) {
        public record Owner(String login) {}
    }

    public boolean isPrMerged() {
        return pullRequest != null && pullRequest.merged();
    }

    public String repoOwner() {
        return repository != null && repository.owner() != null ? repository.owner().login() : null;
    }

    public String repoName() {
        return repository != null ? repository.name() : null;
    }
}
