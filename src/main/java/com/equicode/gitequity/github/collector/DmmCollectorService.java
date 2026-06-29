package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.PrContribution;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.equity.dmm.CommitDmmResult;
import com.equicode.gitequity.equity.dmm.DmmAnalyzer;
import com.equicode.gitequity.repository.PrContributionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * dmm_analyzer.py(pydriller)로 main 브랜치 커밋(=squash merge PR)의 DMM을 분석하고,
 * 커밋 메시지에서 추출한 PR 번호로 pr_contributions와 조인해 dmm_complexity를 채운다.
 *
 * 파이썬 프로세스가 없거나 실패해도 전체 수집은 죽지 않고 DMM을 null로 둔 채 진행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DmmCollectorService {

    private final DmmAnalyzer dmmAnalyzer;
    private final PrContributionRepository prContributionRepository;

    /**
     * @param project  대상 프로젝트
     * @param repoPath 이미 clone되어 있는 로컬 저장소 경로
     * @return dmm_complexity가 갱신된 PR 수
     */
    @Transactional
    public int collect(Project project, String repoPath) {
        if (!dmmAnalyzer.isAvailable()) {
            log.warn("[DMM] pydriller unavailable — install with: pip install -r scripts/requirements.txt");
            return 0;
        }

        List<CommitDmmResult> results;
        try {
            results = dmmAnalyzer.analyze(repoPath);
        } catch (Exception e) {
            log.warn("[DMM] analysis failed project={} repoPath={}: {}",
                    project.getRepoName(), repoPath, e.getMessage());
            return 0;
        }

        int updated = 0;
        for (CommitDmmResult result : results) {
            if (result.prNumber() == null) continue;

            Optional<Double> avgDmm = result.averageDmm();
            if (avgDmm.isEmpty()) continue;

            Optional<PrContribution> pr = prContributionRepository
                    .findByProjectIdAndPrNumber(project.getId(), result.prNumber());
            if (pr.isEmpty()) continue;

            pr.get().applyDmmComplexity(avgDmm.get());
            updated++;
        }

        log.info("[DMM] project={} updated={}/{}", project.getRepoName(), updated, results.size());
        return updated;
    }
}
