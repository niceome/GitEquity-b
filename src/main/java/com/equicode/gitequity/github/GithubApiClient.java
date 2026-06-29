package com.equicode.gitequity.github;

import com.equicode.gitequity.github.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GithubApiClient {

    private final WebClient githubWebClient;

    // ── Commits ──────────────────────────────────────────────────────────────

    public PagedResponse<CommitDto> fetchCommits(String owner, String repo, int page, String token) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/commits")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build(owner, repo))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleError)
                .toEntityList(CommitDto.class)
                .map(resp -> PagedResponse.from(resp.getBody(), resp.getHeaders()))
                .block();
    }

    // ── Pull Requests ────────────────────────────────────────────────────────

    public PagedResponse<PullRequestDto> fetchPullRequests(String owner, String repo, int page, String token) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls")
                        .queryParam("state", "all")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build(owner, repo))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleError)
                .toEntityList(PullRequestDto.class)
                .map(resp -> PagedResponse.from(resp.getBody(), resp.getHeaders()))
                .block();
    }

    // 병합 여부와 무관하게 종료된 PR만 조회 (open PR은 "최종 diff"가 아니므로 제외)
    public PagedResponse<PullRequestDto> fetchClosedPullRequests(String owner, String repo, int page, String token) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls")
                        .queryParam("state", "closed")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build(owner, repo))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleError)
                .toEntityList(PullRequestDto.class)
                .map(resp -> PagedResponse.from(resp.getBody(), resp.getHeaders()))
                .block();
    }

    // ── Reviews ───────────────────────────────────────────────────────────────

    public PagedResponse<PullRequestReviewDto> fetchPullRequestReviews(
            String owner, String repo, int pullNumber, int page, String token) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls/{pull_number}/reviews")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build(owner, repo, pullNumber))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleError)
                .toEntityList(PullRequestReviewDto.class)
                .map(resp -> PagedResponse.from(resp.getBody(), resp.getHeaders()))
                .block();
    }

    // ── PR 인라인 코드 리뷰 코멘트 ───────────────────────────────────────────────

    public PagedResponse<PullRequestReviewCommentDto> fetchPullRequestReviewComments(
            String owner, String repo, int pullNumber, int page, String token) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls/{pull_number}/comments")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build(owner, repo, pullNumber))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleError)
                .toEntityList(PullRequestReviewCommentDto.class)
                .map(resp -> PagedResponse.from(resp.getBody(), resp.getHeaders()))
                .block();
    }

    // ── 이슈/PR 본문 코멘트 (repo 전체) ───────────────────────────────────────────

    public PagedResponse<IssueCommentDto> fetchIssueComments(String owner, String repo, int page, String token) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/issues/comments")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build(owner, repo))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleError)
                .toEntityList(IssueCommentDto.class)
                .map(resp -> PagedResponse.from(resp.getBody(), resp.getHeaders()))
                .block();
    }

    // ── Issues ────────────────────────────────────────────────────────────────

    public PagedResponse<IssueDto> fetchIssues(String owner, String repo, int page, String token) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/issues")
                        .queryParam("state", "all")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build(owner, repo))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleError)
                .toEntityList(IssueDto.class)
                .map(resp -> PagedResponse.from(resp.getBody(), resp.getHeaders()))
                .block();
    }

    // ── PR 변경 파일 목록 ─────────────────────────────────────────────────────

    public PagedResponse<PullRequestFileDto> fetchPullRequestFiles(
            String owner, String repo, int pullNumber, int page, String token) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls/{pull_number}/files")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build(owner, repo, pullNumber))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleError)
                .toEntityList(PullRequestFileDto.class)
                .map(resp -> PagedResponse.from(resp.getBody(), resp.getHeaders()))
                .block();
    }

    // ── 커밋 변경 파일 목록 ───────────────────────────────────────────────────
    // GitHub은 커밋당 최대 300개 파일을 단일 응답으로 반환 (페이지네이션 없음)

    public List<PullRequestFileDto> fetchCommitFiles(String owner, String repo, String sha, String token) {
        CommitFilesDto response = githubWebClient.get()
                .uri("/repos/{owner}/{repo}/commits/{sha}", owner, repo, sha)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleError)
                .bodyToMono(CommitFilesDto.class)
                .block();
        return response != null ? response.safeFiles() : List.of();
    }

    // ── 파일 내용 (base64) ────────────────────────────────────────────────────

    public FileContentDto fetchFileContent(String owner, String repo, String path, String token) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/contents/{path}")
                        .build(owner, repo, path))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleError)
                .bodyToMono(FileContentDto.class)
                .block();
    }

    // ── Repository 존재 확인 ──────────────────────────────────────────────────

    public boolean repositoryExists(String owner, String repo, String token) {
        try {
            githubWebClient.get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::handleError)
                    .bodyToMono(RepositoryDto.class)
                    .block();
            return true;
        } catch (GithubApiException e) {
            if (e.getStatusCode() == 404) return false;
            throw e;
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    private Mono<? extends Throwable> handleError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    int status = response.statusCode().value();
                    if (status == 404) {
                        log.debug("GitHub API 404: {}", body);
                    } else {
                        log.error("GitHub API error: status={}, body={}", response.statusCode(), body);
                    }
                    return Mono.error(new GithubApiException(status, body));
                });
    }
}
