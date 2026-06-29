package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 코드 추적이 없는 비코드 기여(멘토링/설계/디버깅 도움/문서·운영) 인정 요청
// claimer가 등록하고 beneficiary가 확인(CONFIRMED)/반려(REJECTED)한다
@Getter
@Entity
@Table(name = "non_code_contributions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class NonCodeContribution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // 기여를 주장(등록)하는 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimer_id", nullable = false)
    private User claimer;

    // 기여의 수혜자 — 이 사용자만 확인/반려할 수 있다 (자기 확인 금지: claimer != beneficiary)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private User beneficiary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NonCodeContributionType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDate occurredAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.PENDING;

    private LocalDateTime confirmedAt;

    public void confirm() {
        this.status = ClaimStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = ClaimStatus.REJECTED;
    }
}
