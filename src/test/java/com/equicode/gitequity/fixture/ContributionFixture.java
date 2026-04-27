package com.equicode.gitequity.fixture;

import com.equicode.gitequity.domain.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 지분 계산 알고리즘 검증용 더미 데이터
 *
 * 5명의 기여 패턴 (ccnScore=1.0, fileImportance=1.0, count=1 고정):
 *
 *  Alice  — PR 위주  : PR×20, Commit×5,  Review×2,  Issue×1
 *  Bob    — Commit 위주: PR×5,  Commit×40, Review×2,  Issue×3
 *  Carol  — Review 위주: PR×2,  Commit×8,  Review×30, Issue×5
 *  Dave   — 균형형  : PR×8,  Commit×12, Review×10, Issue×10
 *  Eve    — Issue 위주 : PR×3,  Commit×5,  Review×5,  Issue×25
 *
 * 기본 가중치(COMMIT=1.0, PR=3.0, REVIEW=0.5, ISSUE=0.5) 적용 시 기대 점수:
 *  Alice  66.5  (1위)
 *  Bob    57.5  (2위)
 *  Dave   46.0  (3위)
 *  Carol  31.5  (4위)
 *  Eve    29.0  (5위)
 *  합계  230.5
 */
public final class ContributionFixture {

    private ContributionFixture() {}

    // ── 더미 사용자 ──────────────────────────────────────────────────────────
    public static final User ALICE = User.builder().id(1L).githubId(101L).username("alice").build();
    public static final User BOB   = User.builder().id(2L).githubId(102L).username("bob").build();
    public static final User CAROL = User.builder().id(3L).githubId(103L).username("carol").build();
    public static final User DAVE  = User.builder().id(4L).githubId(104L).username("dave").build();
    public static final User EVE   = User.builder().id(5L).githubId(105L).username("eve").build();

    public static final List<User> ALL_USERS = List.of(ALICE, BOB, CAROL, DAVE, EVE);

    // ── 더미 프로젝트 ─────────────────────────────────────────────────────────
    public static final Project PROJECT = Project.builder()
            .id(1L).name("test-project")
            .repoOwner("test-org").repoName("test-repo")
            .owner(ALICE)
            .build();

    // ── 기대 점수 상수 (검증 시 직접 비교) ────────────────────────────────────
    public static final double ALICE_EXPECTED = 66.5;
    public static final double BOB_EXPECTED   = 57.5;
    public static final double CAROL_EXPECTED = 31.5;
    public static final double DAVE_EXPECTED  = 46.0;
    public static final double EVE_EXPECTED   = 29.0;
    public static final double TOTAL_EXPECTED = 230.5;

    // ── 사용자별 기여 목록 ────────────────────────────────────────────────────

    public static List<Contribution> aliceContributions() {
        return build(ALICE, 20, 5, 2, 1);
    }

    public static List<Contribution> bobContributions() {
        return build(BOB, 5, 40, 2, 3);
    }

    public static List<Contribution> carolContributions() {
        return build(CAROL, 2, 8, 30, 5);
    }

    public static List<Contribution> daveContributions() {
        return build(DAVE, 8, 12, 10, 10);
    }

    public static List<Contribution> eveContributions() {
        return build(EVE, 3, 5, 5, 25);
    }

    /** 5명 전체 기여 목록 */
    public static List<Contribution> allContributions() {
        List<Contribution> all = new ArrayList<>();
        all.addAll(aliceContributions());
        all.addAll(bobContributions());
        all.addAll(carolContributions());
        all.addAll(daveContributions());
        all.addAll(eveContributions());
        return all;
    }

    /** N명 × M 기여 건수 성능 테스트용 더미 생성 */
    public static List<Contribution> generateForPerformanceTest(int userCount, int contributionsPerUser) {
        List<Contribution> list = new ArrayList<>(userCount * contributionsPerUser);
        for (int u = 0; u < userCount; u++) {
            User user = User.builder()
                    .id((long) (u + 100))
                    .githubId((long) (u + 10000))
                    .username("user-" + u)
                    .build();
            for (int c = 0; c < contributionsPerUser; c++) {
                ContributionType type = ContributionType.values()[c % ContributionType.values().length];
                list.add(contribution(user, type, "perf-" + u + "-" + c));
            }
        }
        return list;
    }

    // ── 빌더 헬퍼 ─────────────────────────────────────────────────────────────

    private static List<Contribution> build(User user, int prs, int commits, int reviews, int issues) {
        List<Contribution> list = new ArrayList<>();
        for (int i = 0; i < prs;     i++) list.add(contribution(user, ContributionType.PR,     "pr-"     + user.getUsername() + i));
        for (int i = 0; i < commits;  i++) list.add(contribution(user, ContributionType.COMMIT, "sha-"    + user.getUsername() + i));
        for (int i = 0; i < reviews;  i++) list.add(contribution(user, ContributionType.REVIEW, "rev-"    + user.getUsername() + i));
        for (int i = 0; i < issues;   i++) list.add(contribution(user, ContributionType.ISSUE,  "issue-"  + user.getUsername() + i));
        return list;
    }

    public static Contribution contribution(User user, ContributionType type, String githubId) {
        double weight = type.getWeight();
        return Contribution.builder()
                .user(user)
                .project(PROJECT)
                .type(type)
                .githubId(githubId)
                .count(1)
                .ccnScore(1.0)
                .fileImportance(1.0)
                .rawScore(weight)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
