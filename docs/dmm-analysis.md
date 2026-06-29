# DMM(Delta Maintainability Model) 분석 가이드

`pr_contributions.dmm_complexity`를 PyDriller 기반 DMM(Delta Maintainability
Model, di Biase et al. 2019) 분석 결과로 채우는 방법을 설명한다.

## 배경

팀 repo는 squash merge 워크플로를 사용한다. 즉 main 브랜치의 커밋 1개 = PR 1개이며,
squash 커밋 메시지 첫 줄 끝에는 GitHub가 자동으로 `(#PR번호)`를 붙인다. 따라서
PyDriller로 main 브랜치 커밋 단위 DMM을 계산하면 그것이 곧 PR 단위 DMM이 된다.

`dmm_unit_size`/`dmm_unit_complexity`/`dmm_unit_interfacing` 세 지표 중 null이
아닌 값들의 평균을 `dmm_complexity`로 저장한다 (`CommitDmmResult.averageDmm()`).
세 지표가 모두 null인 커밋(측정 대상 파일 없음)은 건너뛰고, 해당 PR의
`dmm_complexity`는 `NULL`로 남는다.

## 사전 설치

```bash
pip install -r scripts/requirements.txt
```

설치 여부는 `DmmAnalyzer.isAvailable()`이 `python3 -c "import pydriller"` 실행
결과로 캐시 확인한다. pydriller가 없으면 `DmmCollectorService.collect()`는 경고
로그만 남기고 0을 반환하며, 전체 수집 과정은 중단되지 않는다.

## dmm_analyzer.py 단독 실행

```bash
python3 dmm_analyzer.py /path/to/repo
```

(`scripts/` 디렉터리에서 실행하거나, 경로를 적절히 조정한다.) 대상 repo는 이미
로컬에 clone되어 있어야 하며, main 브랜치 커밋마다 다음 형태의 JSON 객체를 배열로
stdout에 출력한다:

```json
[
  {
    "hash": "a1b2c3d",
    "message": "feat: add equity calculator (#42)",
    "dmm_unit_size": 1.0,
    "dmm_unit_complexity": 0.83,
    "dmm_unit_interfacing": 1.0,
    "pr_number": 42
  }
]
```

`(#번호)` 패턴이 없는 커밋의 `pr_number`는 `null`이며, 측정 대상 파일이 없는
커밋의 DMM 지표는 `null`이다.

## Spring 연동 실행 (테스트 엔드포인트)

```bash
curl -X POST "http://localhost:8080/api/github/test/dmm/{projectId}?repoPath=/path/to/repo"
```

`DmmCollectorService.collect(project, repoPath)`가 `dmm_analyzer.py`를
ProcessBuilder로 실행하고, 결과를 PR 번호로 `pr_contributions`와 조인하여
`dmm_complexity`를 갱신한다. 응답:

```json
{
  "message": "dmm collection done: updated=5",
  "data": 5
}
```

## 결과 확인 (SQL)

```sql
SELECT pr_number, title, pr_type, net_lines, density, dmm_complexity
FROM pr_contributions
WHERE project_id = {projectId}
ORDER BY pr_number;
```

## 점수 반영 (DmmFactor)

`DmmFactor.factor(Double dmm)`은 DMM 값을 기여 점수 보정 계수로 변환한다:

```
factor = dmm == null ? 1.0 (중립) : factorBase + factorScale * dmm
```

기본값(`factorBase=0.7`, `factorScale=0.6`) 기준 `dmm ∈ [0, 1]` → `factor ∈ [0.7, 1.3]`.
두 상수는 `equity.dmm.factor-base`/`equity.dmm.factor-scale` 설정 키로 오버라이드
가능하다.

## 예외 처리

- pydriller 미설치: `isAvailable()`이 false → 경고 로그 후 0건 처리.
- 파이썬 프로세스 실패/타임아웃(10분)/JSON 파싱 실패: `DmmAnalyzer.analyze()`가
  예외를 던지고, `DmmCollectorService.collect()`가 이를 catch하여 경고 로그 후
  0건 처리. 어느 경우든 `dmm_complexity`는 null로 남고 전체 수집은 중단되지 않는다.
