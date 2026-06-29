#!/usr/bin/env python3
"""PR(squash 커밋) 단위 DMM(Delta Maintainability Model, di Biase et al. 2019) 분석기.

팀 repo는 squash merge 워크플로를 사용하므로 main 브랜치의 커밋 1개 = PR 1개이며,
squash 커밋 메시지 첫 줄 끝에는 GitHub가 자동으로 "(#PR번호)"를 붙인다.
따라서 커밋 단위 DMM이 곧 PR 단위 DMM이 된다.

사용법:
    python3 dmm_analyzer.py /path/to/repo

입력 repo는 이미 로컬에 clone되어 있다고 가정한다 (기존 Lizard 파이프라인과 동일 방식).
main 브랜치 커밋마다 dmm_unit_size/complexity/interfacing과 PR 번호를 JSON 배열로
stdout에 출력한다. 분석 불가(해당 커밋에 측정 대상 파일이 없는 등)로 None이 반환된
지표는 JSON에서 null로 표현된다.
"""
import json
import re
import sys

from pydriller import Repository

PR_NUMBER_PATTERN = re.compile(r"\(#(\d+)\)$")


def extract_pr_number(message_first_line: str):
    match = PR_NUMBER_PATTERN.search(message_first_line)
    return int(match.group(1)) if match else None


def analyze(repo_path: str):
    results = []
    for commit in Repository(repo_path, only_in_branch="main").traverse_commits():
        first_line = commit.msg.split("\n", 1)[0]
        results.append({
            "hash": commit.hash,
            "message": first_line,
            "dmm_unit_size": commit.dmm_unit_size,
            "dmm_unit_complexity": commit.dmm_unit_complexity,
            "dmm_unit_interfacing": commit.dmm_unit_interfacing,
            "pr_number": extract_pr_number(first_line),
        })
    return results


def main():
    if len(sys.argv) != 2:
        print("usage: python3 dmm_analyzer.py /path/to/repo", file=sys.stderr)
        sys.exit(1)

    json.dump(analyze(sys.argv[1]), sys.stdout)


if __name__ == "__main__":
    main()
