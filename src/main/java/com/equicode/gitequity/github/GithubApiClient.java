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
                    log.error("GitHub API error: status={}, body={}", response.statusCode(), body);
                    return Mono.error(new GithubApiException(response.statusCode().value(), body));
                });
    }
}
