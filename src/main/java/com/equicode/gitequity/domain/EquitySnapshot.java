package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 특정 시점의 프로젝트 내 유저 지분 스냅샷
// 계약 생성 시 snapshot_group_id로 묶어 Contract와 연결
@Getter
@Entity
@Table(name = "equity_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EquitySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 해당 유저의 지분 비율 (%)
    @Column(nullable = false)
    private double percentage;

    private int totalCommits;
    private int totalPrs;
    private int totalReviews;
    private int totalIssues;

    @Column(nullable = false)
    private double rawScore;

    @Column(nullable = false)
    private LocalDateTime snapshotAt;

    // 계약 생성 시 연결되는 계약 (nullable — 스냅샷만 저장하고 계약 미생성 케이스 존재)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    public void linkContract(Contract contract) {
        this.contract = contract;
    }
}
