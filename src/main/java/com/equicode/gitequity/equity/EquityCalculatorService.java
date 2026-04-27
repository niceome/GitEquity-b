package com.equicode.gitequity.equity;

import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.common.exception.ErrorCode;
import com.equicode.gitequity.domain.Contribution;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.repository.ContributionRepository;
import com.equicode.gitequity.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Phase E — 통합 지분 계산 서비스
 *
 * 계산 공식:
 *   userScore  = Σ (type.weight × count × ccnScore × fileImportance) per contribution
 *   percentage = userScore / grandTotal × 100
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquityCalculatorService {

    private final ContributionRepository contributionRepository;
    private final ProjectRepository      projectRepository;
    private final TypeScoreCalculator    typeScoreCalculator;

    @Transactional(readOnly = true)
    public EquityResult calculate(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        List<Contribution> contributions = contributionRepository.findByProjectId(projectId);

        if (contributions.isEmpty()) {
            log.info("[Equity] project={} has no contributions", projectId);
            return new EquityResult(projectId, List.of(), 0.0, LocalDateTime.now());
        }

        Map<Long, UserScore> scores = typeScoreCalculator.calculate(
                contributions, project.getWeightConfig());

        double grandTotal = scores.values().stream()
                .mapToDouble(UserScore::total)
                .sum();

        List<UserEquity> equities = scores.values().stream()
                .map(s -> new UserEquity(
                        s.userId(),
                        s.username(),
                        s.total(),
                        s.equityPercent(grandTotal),
                        s.byType()))
                .sorted(Comparator.comparingDouble(UserEquity::percentage).reversed())
                .toList();

        EquityResult result = new EquityResult(projectId, equities, grandTotal, LocalDateTime.now());

        log.info("[Equity] project={} contributors={} grandTotal={}",
                projectId, equities.size(), String.format("%.2f", grandTotal));
        equities.forEach(e ->
                log.info("  {} → rawScore={} equity={}%",
                        e.username(),
                        String.format("%.2f", e.rawScore()),
                        String.format("%.2f", e.percentage())));

        return result;
    }
}
