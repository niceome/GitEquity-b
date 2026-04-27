package com.equicode.gitequity.equity;

import com.equicode.gitequity.domain.Contribution;
import com.equicode.gitequity.domain.ContributionType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 레이어 1 — 기여 유형별 가중치 적용 점수 계산기
 *
 * 공식: score = count × ccnScore × fileImportance × effectiveWeight(type)
 *
 * effectiveWeight 우선순위:
 *   1. 프로젝트 weightConfig 에 해당 type 키가 있으면 사용
 *   2. 없으면 ContributionType.getWeight() 기본값 사용
 */
@Component
public class TypeScoreCalculator {

    /**
     * @param contributions 수집된 기여 목록
     * @param weightConfig  프로젝트 커스텀 가중치 (null 또는 빈 맵이면 기본값 사용)
     * @return userId → UserScore 맵
     */
    public Map<Long, UserScore> calculate(List<Contribution> contributions,
                                          Map<String, Double> weightConfig) {
        Map<Long, Accumulator> acc = new LinkedHashMap<>();

        for (Contribution c : contributions) {
            if (c.getUser() == null) continue;

            double weight = effectiveWeight(c.getType(), weightConfig);
            double score  = c.getCount() * c.getCcnScore() * c.getFileImportance() * weight;

            acc.computeIfAbsent(c.getUser().getId(),
                            id -> new Accumulator(id, c.getUser().getUsername()))
                    .add(c.getType(), score);
        }

        Map<Long, UserScore> result = new LinkedHashMap<>();
        acc.forEach((id, a) -> result.put(id, a.toUserScore()));
        return result;
    }

    public double effectiveWeight(ContributionType type, Map<String, Double> weightConfig) {
        if (weightConfig != null && weightConfig.containsKey(type.name())) {
            return weightConfig.get(type.name());
        }
        return type.getWeight();
    }

    // ── 내부 누적기 ────────────────────────────────────────────────────────────

    private static final class Accumulator {
        private final Long userId;
        private final String username;
        private final Map<ContributionType, Double> byType = new EnumMap<>(ContributionType.class);

        Accumulator(Long userId, String username) {
            this.userId   = userId;
            this.username = username;
        }

        void add(ContributionType type, double score) {
            byType.merge(type, score, Double::sum);
        }

        UserScore toUserScore() {
            double total = byType.values().stream().mapToDouble(Double::doubleValue).sum();
            return new UserScore(userId, username, Collections.unmodifiableMap(new EnumMap<>(byType)), total);
        }
    }
}
