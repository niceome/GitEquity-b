package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.Contribution;
import com.equicode.gitequity.domain.ContributionType;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.CommitDto;
import com.equicode.gitequity.github.dto.PagedResponse;
import com.equicode.gitequity.repository.ContributionRepository;
import com.equicode.gitequity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommitCollectorService {

    private final GithubApiClient githubApiClient;
    private final ContributionRepository contributionRepository;
    private final UserRepository userRepository;

    @Transactional
    public int collect(Project project, String token) {
        List<Contribution> toSave = new ArrayList<>();
        int page = 1;

        while (true) {
            PagedResponse<CommitDto> response = githubApiClient.fetchCommits(
                    project.getRepoOwner(), project.getRepoName(), page, token);

            for (CommitDto dto : response.items()) {
                // GitHub 계정 미연동 커밋 또는 봇 제외
                if (dto.author() == null || dto.author().isBot()) continue;

                // GitEquity에 등록된 사용자만 수집
                Optional<User> userOpt = userRepository.findByUsername(dto.author().login());
                if (userOpt.isEmpty()) continue;

                // 중복 수집 방지
                if (contributionRepository.existsByProjectIdAndTypeAndGithubId(
                        project.getId(), ContributionType.COMMIT, dto.sha())) continue;

                toSave.add(Contribution.builder()
                        .user(userOpt.get())
                        .project(project)
                        .type(ContributionType.COMMIT)
                        .githubId(dto.sha())
                        .count(1)
                        .ccnScore(1.0)
                        .fileImportance(1.0)
                        .rawScore(ContributionType.COMMIT.getWeight())
                        .occurredAt(CollectorUtils.parseIso(dto.commit().author().date()))
                        .build());
            }

            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }

        contributionRepository.saveAll(toSave);
        log.info("[Commit] project={} saved={}", project.getRepoName(), toSave.size());
        return toSave.size();
    }
}
