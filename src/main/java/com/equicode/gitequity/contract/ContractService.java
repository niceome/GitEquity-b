package com.equicode.gitequity.contract;

import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.common.exception.ErrorCode;
import com.equicode.gitequity.contract.dto.ContractDetailResponse;
import com.equicode.gitequity.contract.dto.ContractResponse;
import com.equicode.gitequity.contract.dto.MemberSignatureStatus;
import com.equicode.gitequity.domain.*;
import com.equicode.gitequity.contract.dto.ContractPdfData;
import com.equicode.gitequity.contract.pdf.ContractPdfService;
import com.equicode.gitequity.contract.storage.ContractStorageService;
import com.equicode.gitequity.email.EmailService;
import com.equicode.gitequity.equity.EquitySnapshotService;
import com.equicode.gitequity.equity.SnapshotSummary;
import com.equicode.gitequity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository       contractRepository;
    private final SignatureRepository      signatureRepository;
    private final ProjectRepository        projectRepository;
    private final ProjectMemberRepository  projectMemberRepository;
    private final UserRepository           userRepository;
    private final EquitySnapshotRepository snapshotRepository;
    private final EquitySnapshotService    snapshotService;
    private final EmailService             emailService;
    private final ContractPdfService       pdfService;
    private final ContractStorageService   storageService;

    // ── Phase A: 계약 초안 생성 ───────────────────────────────────────────────

    @Transactional
    public ContractResponse createContract(Long projectId, Long requestUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        // OWNER만 계약 생성 가능
        ProjectMember requester = projectMemberRepository
                .findByProjectIdAndUserId(projectId, requestUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));
        if (requester.getRole() != MemberRole.OWNER) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 최신 스냅샷 조회 → 없으면 새로 생성
        LocalDateTime snapshotAt = snapshotRepository
                .findTopByProjectIdOrderBySnapshotAtDesc(projectId)
                .map(EquitySnapshot::getSnapshotAt)
                .orElseGet(() -> {
                    SnapshotSummary created = snapshotService.createSnapshot(projectId);
                    return created.snapshotAt();
                });

        // snapshotGroupId = snapshotAt epoch second (배치 식별자)
        long snapshotGroupId = snapshotAt.toEpochSecond(java.time.ZoneOffset.UTC);

        Contract contract = Contract.builder()
                .project(project)
                .status(ContractStatus.DRAFT)
                .snapshotGroupId(snapshotGroupId)
                .createdAt(LocalDateTime.now())
                .build();
        contract = contractRepository.save(contract);

        // 스냅샷에 계약 연결
        List<EquitySnapshot> snapshots = snapshotRepository
                .findByProjectIdAndSnapshotAt(projectId, snapshotAt);
        for (EquitySnapshot snapshot : snapshots) {
            snapshot.linkContract(contract);
        }

        // 멤버 전원에 대한 미서명 Signature 레코드 생성
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        final Contract finalContract = contract;
        List<Signature> signatures = members.stream()
                .map(m -> Signature.builder()
                        .contract(finalContract)
                        .user(m.getUser())
                        .build())
                .toList();
        signatureRepository.saveAll(signatures);

        log.info("[Contract] created id={} project={} members={}", contract.getId(), projectId, members.size());
        return ContractResponse.from(contract);
    }

    // ── Phase A: 계약 상세 조회 ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ContractDetailResponse getContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        List<Signature> signatures = signatureRepository.findByContractId(contractId);
        List<EquitySnapshot> snapshots = snapshotRepository.findByContractId(contractId);

        // snapshotId → percentage 매핑
        Map<Long, Double> percentageByUser = snapshots.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), EquitySnapshot::getPercentage));

        List<MemberSignatureStatus> members = signatures.stream()
                .map(sig -> new MemberSignatureStatus(
                        sig.getUser().getId(),
                        sig.getUser().getUsername(),
                        percentageByUser.getOrDefault(sig.getUser().getId(), 0.0),
                        sig.getSignedAt() != null,
                        sig.getSignedAt()))
                .sorted((a, b) -> Double.compare(b.percentage(), a.percentage()))
                .toList();

        long signedCount = signatures.stream().filter(s -> s.getSignedAt() != null).count();

        return new ContractDetailResponse(
                contract.getId(),
                contract.getProject().getId(),
                contract.getProject().getName(),
                contract.getStatus(),
                contract.getPdfUrl(),
                contract.getCreatedAt(),
                contract.getCompletedAt(),
                signatures.size(),
                signedCount,
                members);
    }

    // ── Phase A: 서명 요청 발송 (DRAFT → PENDING) ─────────────────────────────

    @Transactional
    public ContractResponse sendContract(Long contractId, Long requestUserId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new CustomException(ErrorCode.CONTRACT_NOT_SENDABLE);
        }

        // OWNER 검증
        projectMemberRepository.findByProjectIdAndUserId(
                contract.getProject().getId(), requestUserId)
                .filter(m -> m.getRole() == MemberRole.OWNER)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN));

        contract.send();

        // Phase C: 멤버 전원에게 서명 요청 이메일 발송 (비동기)
        ContractDetailResponse detail = getContract(contractId);
        List<Signature> sigs = signatureRepository.findByContractId(contractId);
        sigs.forEach(sig -> {
            String email = sig.getUser().getEmail();
            if (email != null && !email.isBlank()) {
                emailService.sendSignRequest(
                        email,
                        sig.getUser().getUsername(),
                        contract.getProject().getName(),
                        contractId,
                        detail.members());
            }
        });

        return ContractResponse.from(contract);
    }

    // ── Phase B: 전자서명 ──────────────────────────────────────────────────────

    @Transactional
    public ContractDetailResponse sign(Long contractId, Long userId, String ipAddress) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStatus() != ContractStatus.PENDING) {
            throw new CustomException(ErrorCode.CONTRACT_NOT_SIGNABLE);
        }

        Signature signature = signatureRepository
                .findByContractIdAndUserId(contractId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.SIGNATURE_NOT_FOUND));

        if (signature.getSignedAt() != null) {
            throw new CustomException(ErrorCode.ALREADY_SIGNED);
        }

        signature.sign(ipAddress);

        // 전원 서명 완료 여부 확인
        long total  = signatureRepository.countByContractId(contractId);
        long signed = signatureRepository.countByContractIdAndSignedAtIsNotNull(contractId);
        if (signed == total) {
            log.info("[Contract] all {} members signed contractId={} — triggering completion", total, contractId);
            onAllSigned(contract);
        }

        return getContract(contractId);
    }

    // ── 전원 서명 완료 후 처리 ───────────────────────────────────────────────

    protected void onAllSigned(Contract contract) {
        Long contractId = contract.getId();

        // 1. 스냅샷 + 서명 데이터 수집
        List<EquitySnapshot> snapshots = snapshotRepository.findByContractId(contractId);
        List<Signature> signatures     = signatureRepository.findByContractId(contractId);

        Map<Long, Signature> sigByUser = signatures.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        List<ContractPdfData.MemberPdfRow> rows = snapshots.stream()
                .map(snap -> {
                    Signature sig = sigByUser.get(snap.getUser().getId());
                    return new ContractPdfData.MemberPdfRow(
                            snap.getUser().getUsername(),
                            snap.getPercentage(),
                            snap.getRawScore(),
                            snap.getTotalCommits(),
                            snap.getTotalPrs(),
                            snap.getTotalReviews(),
                            snap.getTotalIssues(),
                            sig != null ? sig.getSignedAt() : null,
                            sig != null ? sig.getIpAddress() : null);
                })
                .sorted((a, b) -> Double.compare(b.percentage(), a.percentage()))
                .toList();

        ContractPdfData pdfData = new ContractPdfData(
                contractId,
                contract.getProject().getName(),
                contract.getProject().getRepoOwner(),
                contract.getProject().getRepoName(),
                contract.getCreatedAt(),
                LocalDateTime.now(),
                rows);

        // 2. PDF 생성 → 저장 → 계약 완료
        byte[] pdfBytes = pdfService.generate(pdfData);
        String pdfUrl   = storageService.uploadPdf(pdfBytes, contractId);
        contract.complete(pdfUrl);
        log.info("[Contract] completed id={} pdfUrl={}", contractId, pdfUrl);

        // 3. 완료 이메일 (비동기)
        List<MemberSignatureStatus> memberStatuses = getContract(contractId).members();
        signatures.forEach(sig -> {
            String email = sig.getUser().getEmail();
            if (email != null && !email.isBlank()) {
                emailService.sendContractCompleted(
                        email,
                        sig.getUser().getUsername(),
                        contract.getProject().getName(),
                        pdfUrl,
                        memberStatuses);
            }
        });
    }

    // ── 목록 조회 ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContractResponse> listContracts(Long projectId) {
        return contractRepository.findByProjectId(projectId).stream()
                .map(ContractResponse::from)
                .toList();
    }

    // ── Phase F: PDF 다운로드 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PdfDownloadResult downloadPdf(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStatus() == ContractStatus.COMPLETED && contract.getPdfUrl() != null) {
            return PdfDownloadResult.redirect(contract.getPdfUrl());
        }

        // DRAFT/PENDING: 미리보기 PDF 온더플라이 생성
        List<EquitySnapshot> snapshots = snapshotRepository.findByContractId(contractId);
        List<Signature> signatures     = signatureRepository.findByContractId(contractId);

        Map<Long, Signature> sigByUser = signatures.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        List<ContractPdfData.MemberPdfRow> rows = snapshots.stream()
                .map(snap -> {
                    Signature sig = sigByUser.get(snap.getUser().getId());
                    return new ContractPdfData.MemberPdfRow(
                            snap.getUser().getUsername(),
                            snap.getPercentage(),
                            snap.getRawScore(),
                            snap.getTotalCommits(),
                            snap.getTotalPrs(),
                            snap.getTotalReviews(),
                            snap.getTotalIssues(),
                            sig != null ? sig.getSignedAt() : null,
                            sig != null ? sig.getIpAddress() : null);
                })
                .sorted((a, b) -> Double.compare(b.percentage(), a.percentage()))
                .toList();

        ContractPdfData pdfData = new ContractPdfData(
                contractId,
                contract.getProject().getName(),
                contract.getProject().getRepoOwner(),
                contract.getProject().getRepoName(),
                contract.getCreatedAt(),
                null,
                rows);

        byte[] pdfBytes = pdfService.generate(pdfData);
        return PdfDownloadResult.bytes(pdfBytes, "contract-" + contractId + "-preview.pdf");
    }

    public record PdfDownloadResult(boolean isRedirect, String url, byte[] bytes, String filename) {
        static PdfDownloadResult redirect(String url) {
            return new PdfDownloadResult(true, url, null, null);
        }
        static PdfDownloadResult bytes(byte[] bytes, String filename) {
            return new PdfDownloadResult(false, null, bytes, filename);
        }
    }
}
