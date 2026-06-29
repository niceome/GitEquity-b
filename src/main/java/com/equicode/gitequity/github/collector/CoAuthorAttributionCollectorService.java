package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.PrContribution;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.equity.quality.CoAuthorParser;
import com.equicode.gitequity.equity.quality.CoAuthorParser.CoAuthor;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.CommitDto;
import com.equicode.gitequity.github.dto.PagedResponse;
import com.equicode.gitequity.repository.PrContributionRepository;
import com.equicode.gitequity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * squash 커밋 메시지의 Co-authored-by 트레일러를 파싱하여 pr_contributions의
 * co_author_github_ids를 채운다 (S12).
 *
 * - 메시지 첫 줄의 "(#PR번호)"로 pr_contributions와 매칭 (dmm_analyzer.py와 동일 규약)
 * - Co-authored-by 이메일을 User.email과 매칭하여 githubId로 변환
 * - 매칭에 성공한 공동 작성자만 분할 대상에 포함하고, 실패한 이메일은
 *   분할에서 제외한 채 경고 로그만 남긴다
 * - PR 작성자 본인이 Co-authored-by에 포함된 경우는 제외한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoAuthorAttributionCollectorService {

    private final GithubApiClient githubApiClient;
    private final PrContributionRepository prContributionRepository;
    private final UserRepository userRepository;
    private final CoAuthorParser coAuthorParser;

    @Transactional
    public int collect(Project project, String token) {
        int updated = 0;
        int page = 1;

        while (true) {
            PagedResponse<CommitDto> response = githubApiClient.fetchCommits(
                    project.getRepoOwner(), project.getRepoName(), page, token);

            for (CommitDto dto : response.items()) {
                if (apply(project, dto)) updated++;
            }

            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            CollectorUtils.warnIfRateLimitLow(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }

        log.info("[CoAuthor] project={} updated={}", project.getRepoName(), updated);
        return updated;
    }

    private boolean apply(Project project, CommitDto dto) {
        if (dto.commit() == null) return false;

        String message = dto.commit().message();
        Integer prNumber = coAuthorParser.extractPrNumber(message);
        if (prNumber == null) return false;

        List<CoAuthor> coAuthors = coAuthorParser.extractCoAuthors(message);
        if (coAuthors.isEmpty()) return false;

        Optional<PrContribution> prOpt =
                prContributionRepository.findByProjectIdAndPrNumber(project.getId(), prNumber);
        if (prOpt.isEmpty()) return false;

        PrContribution pr = prOpt.get();
        Set<Long> matchedGithubIds = new LinkedHashSet<>();

        for (CoAuthor coAuthor : coAuthors) {
            Optional<User> user = userRepository.findByEmail(coAuthor.email());
            if (user.isEmpty()) {
                log.warn("[CoAuthor] project={} pr=#{} co-author email={} matched no registered user — split skipped for this co-author",
                        project.getRepoName(), prNumber, coAuthor.email());
                continue;
            }

            Long githubId = user.get().getGithubId();
            if (!githubId.equals(pr.getAuthorGithubId())) {
                matchedGithubIds.add(githubId);
            }
        }

        if (matchedGithubIds.isEmpty()) return false;

        pr.applyCoAuthors(new ArrayList<>(matchedGithubIds));
        return true;
    }
}
