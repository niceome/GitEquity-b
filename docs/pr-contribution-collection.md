# PR Contribution 수집 가이드

측정 단위를 "커밋 수"에서 "PR 최종 diff 실질 변경량"으로 전환하기 위한 데이터 수집
레이어(`pr_contributions` 테이블)의 동작 방식과 수동 실행/검증 방법을 설명한다.

## 무엇을 수집하는가

`PrContributionCollectorService`가 프로젝트의 GitHub 저장소에서
`GET /repos/{owner}/{repo}/pulls?state=closed&per_page=100`을 페이지네이션하며 모든
"종료된 PR"(병합 PR + 병합되지 않고 닫힌 PR)을 조회한다. PR마다
`GET /repos/{owner}/{repo}/pulls/{number}/files`를 호출해 변경 파일의
`additions`/`deletions`와 patch 기반 density를 집계하고, PR 제목/라벨로 `pr_type`을
분류하여 `pr_contributions` 테이블에 1건씩 저장한다.

- `merged_at != null` → `is_merged = true` ("정식 기여")
- `merged_at == null && state == closed` → `is_merged = false` ("탐색 기여", 이후 단계에서 0.3 가중치 적용 예정)
- `raw_patch`(diff 본문)는 저장하지 않는다 — 파일별 density 계산에만 사용하고 즉시 버린다.
- 이미 저장된 PR(`project_id` + `pr_number` 중복)과 draft PR, bot 작성 PR은 건너뛴다.
- `net_lines`/`density`는 `DensityCalculator`가 각 파일의 patch에서 "실질 변경 라인"만
  추출해 계산한다 (`net_lines` = 실질 라인 수, `density` = net_lines / gross 변경 라인 수).
- `pr_type`은 `PrTypeClassifier`가 라벨 → Conventional Commits prefix → 키워드
  휴리스틱 순으로 PR 제목/라벨을 분류한 결과(`FEAT`/`FIX`/`REFACTOR`/`TEST`/`DOCS`/
  `CHORE`/`STYLE`/`UNKNOWN`)다.
- `dmm_complexity`는 후속 단계(질적 지표 결합)에서 채워지는 nullable 컬럼이다.

테이블은 `ddl-auto: update` 설정에 의해 애플리케이션 기동 시 자동 생성되므로 별도
마이그레이션이 필요 없다.

## 사전 준비

1. PostgreSQL 컨테이너 기동: `docker-compose up -d` (또는 기존 로컬 DB 사용)
2. 백엔드 서버 기동: `./gradlew bootRun`
3. GitHub OAuth 로그인을 완료한 사용자 계정 — `users.access_token`이 저장되어 있어야
   GitHub API 호출이 가능하다 (`resolveToken`/`collectContributions`가 이 토큰을 사용).
4. 수집 대상 프로젝트가 등록되어 있어야 한다 — `repoOwner`/`repoName`이 실제 GitHub
   저장소를 가리켜야 한다.

   프로젝트가 없다면 다음으로 생성:

   ```bash
   curl -X POST http://localhost:8080/api/projects \
     -H "Authorization: Bearer {JWT}" \
     -H "Content-Type: application/json" \
     -d '{"name": "test-project", "repoOwner": "octocat", "repoName": "Hello-World"}'
   ```

   응답의 `id`가 이후 단계에서 사용할 `{projectId}`다.

## 수집 실행

두 가지 엔드포인트 모두 내부적으로 `ContributionCollectionService.collectAll`을 호출하며,
이 안에서 5가지 기여 유형(commit, PR, review, issue, prContribution)을 병렬로 수집한다.

### 운영 엔드포인트

```bash
curl -X POST http://localhost:8080/api/projects/{projectId}/collect \
  -H "Authorization: Bearer {JWT}"
```

### 테스트 엔드포인트 (Phase C 검증용)

```bash
curl -X POST http://localhost:8080/api/github/test/collect/{projectId} \
  -H "Authorization: Bearer {JWT}"
```

두 엔드포인트 모두 응답으로 `CollectionResult`를 반환한다:

```json
{
  "message": "collection done: total=37 (commit=20, pr=5, review=4, issue=3, prContribution=5)",
  "data": {
    "commits": 20,
    "pullRequests": 5,
    "reviews": 4,
    "issues": 3,
    "prContributions": 5
  }
}
```

`prContributions`가 0보다 크면 `pr_contributions` 테이블에 행이 저장되었다는 뜻이다.

## 결과 확인 (SQL)

```sql
SELECT id, project_id, author_github_id, pr_number, title, is_merged, merged_at,
       additions, deletions, net_lines, density, pr_type, dmm_complexity, labels
FROM pr_contributions
WHERE project_id = {projectId}
ORDER BY pr_number;
```

확인 포인트:

- 닫힌 PR 수만큼 행이 생성되었는지 (`is_merged = true`/`false` 둘 다 포함)
- `additions`/`deletions`가 GitHub PR 페이지의 "Files changed" 탭과 일치하는지
- `net_lines` <= `additions + deletions`이고, `density`가 0~1 사이 값인지
  (patch가 없는 대용량 파일은 집계에서 제외되므로 등호가 아닐 수 있음)
- `pr_type`이 `FEAT`/`FIX`/`REFACTOR`/`TEST`/`DOCS`/`CHORE`/`STYLE`/`UNKNOWN` 중
  하나로 채워졌는지
- `dmm_complexity`는 `NULL`인지 (후속 단계 전까지)
- `raw_patch` 컬럼 자체가 존재하지 않는지 (diff 본문 미저장 확인)

## Rate Limit 처리

- 응답 헤더 `X-RateLimit-Remaining`이 0이면 `X-RateLimit-Reset` 시각까지 블로킹
  대기 후 재시도한다 (`CollectorUtils.waitIfExhausted`).
- `X-RateLimit-Remaining`이 50 미만이면 다음과 같은 경고 로그를 남기고 계속
  진행한다 (`CollectorUtils.warnIfRateLimitLow`):

  ```
  WARN ... GitHub rate limit low — remaining=42, resetAt=1718256000
  ```

  이 로그는 PR 목록 페이지 조회와 PR 파일 목록 페이지 조회 양쪽 모두에서 출력될 수
  있으므로, 대량 수집 시 콘솔에서 해당 로그 메시지를 grep하면 된다.

## 자동화 테스트

`PrContributionCollectorService`에 대한 통합 테스트는 아직 작성되지 않았다 (외부
GitHub API 의존성 때문에 위 수동 절차로 검증). 단위 테스트를 추가할 경우
`GithubApiClient`를 mock하여 `fetchClosedPullRequests`/`fetchPullRequestFiles`
응답을 스텁하고, `PrContributionRepository.saveAll`에 전달된 엔티티의 필드값을
검증하는 방식을 권장한다.
