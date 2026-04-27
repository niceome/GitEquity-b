package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 프로젝트 지분 계약서
// snapshot_group_id: 이 계약에 사용된 EquitySnapshot 묶음 식별자
// DRAFT → PENDING(PDF 업로드 후) → COMPLETED(전원 서명)
@Getter
@Entity
@Table(name = "contracts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status;

    // EquitySnapshot 여러 건을 논리적으로 묶는 그룹 ID (snapshot_at 기준 동일 시각 묶음)
    @Column(nullable = false)
    private Long snapshotGroupId;

    // iText7로 생성 후 S3 업로드된 PDF URL
    private String pdfUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    // DRAFT → PENDING: 서명 요청 발송
    public void send() {
        this.status = ContractStatus.PENDING;
    }

    // PDF 생성 완료 후 URL 저장 (status 변경 없음)
    public void uploadPdf(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    // 전원 서명 + PDF 저장 완료
    public void complete(String pdfUrl) {
        this.pdfUrl = pdfUrl;
        this.status = ContractStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = ContractStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
}
