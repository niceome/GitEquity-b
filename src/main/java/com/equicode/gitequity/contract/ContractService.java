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
import com.equicode.gitequity.equity.EquityCalculatorService;
import com.equicode.gitequity.equity.EquitySnapshotService;
import com.equicode.gitequity.equity.SnapshotSummary;
import com.equicode.gitequity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    // ── INITIAL 계약 생성 (PLANNING 단계, OWNER 전용) ─────────────────────────

    @Transactional
    public ContractResponse createContract(Long projectId, Long requestUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        requireOwner(projectId, requestUserId);

        if (project.getPhase() != ProjectPhase.PLANNING) {
            throw new CustomException(ErrorCode.INVALID_PROJECT_PHASE);
        }

        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);

        Contract contract = Contract.builder()
                .project(project)
                .status(ContractStatus.DRAFT)
                .contractType(ContractType.INITIAL)
                .createdAt(LocalDateTime.now())
                .build();
        contract = contractRepository.save(contract);

        final Contract fc = contract;
        List<Signature> signatures = members.stream()
                .map(m -> Signature.builder().contract(fc).user(m.getUser()).build())
                .toList();
        signatureRepository.saveAll(signatures);

        log.info("[Contract] INITIAL created id={} project={} members={}", contract.getId(), projectId, members.size());
        return ContractResponse.from(contract);
    }

    // ── FINAL 계약 생성 (ProjectService.completeProject() 에서 호출) ──────────

    @Transactional
    public ContractResponse createFinalContract(Project project) {
        Long projectId = project.getId();

        SnapshotSummary snapshot = snapshotService.createSnapshot(projectId);
        if (snapshot.users().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PROJECT_PHASE);
        }

        LocalDateTime snapshotAt = snapshot.snapshotAt();
        long snapshotGroupId = snapshotAt.toEpochSecond(ZoneOffset.UTC);

        Contract contract = Contract.builder()
                .project(project)
                .status(ContractStatus.DRAFT)
                .contractType(ContractType.FINAL)
                .snapshotGroupId(snapshotGroupId)
                .createdAt(LocalDateTime.now())
                .build();
        contract = contractRepository.save(contract);

        List<EquitySnapshot> snapshots = snapshotRepository.findByProjectIdAndSnapshotAt(projectId, snapshotAt);
        for (EquitySnapshot s : snapshots) s.linkContract(contract);

        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        final Contract fc = contract;
        signatureRepository.saveAll(members.stream()
                .map(m -> Signature.builder().contract(fc).user(m.getUser()).build())
                .toList());

        log.info("[Contract] FINAL created id={} project={}", contract.getId(), projectId);
        return ContractResponse.from(contract);
    }

    // ── 계약 상세 조회 ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ContractDetailResponse getContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        ContractType type = contract.getContractType();
        List<Signature> signatures = signatureRepository.findByContractId(contractId);

        // 실제 지분 (EquitySnapshot — FINAL 계약에만 존재)
        Map<Long, Double> finalEquityByUser = Map.of();
        if (type == ContractType.FINAL) {
            finalEquityByUser = snapshotRepository.findByContractId(contractId).stream()
                    .collect(Collectors.toMap(s -> s.getUser().getId(), EquitySnapshot::getPercentage));
        }

        final Map<Long, Double> finalEq = finalEquityByUser;
        List<MemberSignatureStatus> members = signatures.stream()
                .map(sig -> {
                    Long uid = sig.getUser().getId();
                    double pct = finalEq.getOrDefault(uid, 0.0);
                    return new MemberSignatureStatus(
                            uid, sig.getUser().getUsername(),
                            pct,
                            sig.getSignedAt() != null, sig.getSignedAt());
                })
                .sorted((a, b) -> Double.compare(b.percentage(), a.percentage()))
                .toList();

        long signedCount = signatures.stream().filter(s -> s.getSignedAt() != null).count();

        return new ContractDetailResponse(
                contract.getId(), contract.getProject().getId(), contract.getProject().getName(),
                type, contract.getStatus(), contract.getPdfUrl(),
                contract.getCreatedAt(), contract.getCompletedAt(),
                signatures.size(), signedCount, members);
    }

    // ── 서명 요청 발송 (DRAFT → PENDING) ─────────────────────────────────────

    @Transactional
    public ContractResponse sendContract(Long contractId, Long requestUserId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new CustomException(ErrorCode.CONTRACT_NOT_SENDABLE);
        }

        requireOwner(contract.getProject().getId(), requestUserId);
        contract.send();

        return ContractResponse.from(contract);
    }

    // ── 전자서명 ──────────────────────────────────────────────────────────────

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

        signature.sign(ipAddress, EquityCalculatorService.ALGORITHM_VERSION);

        long total  = signatureRepository.countByContractId(contractId);
        long signed = signatureRepository.countByContractIdAndSignedAtIsNotNull(contractId);
        if (signed == total) {
            log.info("[Contract] all {} members signed contractId={}", total, contractId);
            onAllSigned(contract);
        }

        return getContract(contractId);
    }

    // ── 전원 서명 완료 처리 ───────────────────────────────────────────────────

    protected void onAllSigned(Contract contract) {
        if (contract.getContractType() == ContractType.INITIAL) {
            onInitialContractAllSigned(contract);
        } else {
            onFinalContractAllSigned(contract);
        }
    }

    private void onInitialContractAllSigned(Contract contract) {
        Long contractId = contract.getId();
        Long projectId = contract.getProject().getId();

        // 프로젝트 ACTIVE로 전환
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        project.startProject();

        // 참여자 명단만 포함한 PDF 생성 (지분 미확정)
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        List<Signature> signatures = signatureRepository.findByContractId(contractId);
        Map<Long, Signature> sigByUser = signatures.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        List<ContractPdfData.MemberPdfRow> rows = members.stream()
                .map(m -> {
                    Signature sig = sigByUser.get(m.getUser().getId());
                    return new ContractPdfData.MemberPdfRow(
                            m.getUser().getUsername(),
                            null,
                            0.0, 0, 0, 0, 0,
                            sig != null ? sig.getSignedAt() : null,
                            sig != null ? sig.getIpAddress() : null);
                })
                .sorted((a, b) -> a.username().compareTo(b.username()))
                .toList();

        ContractPdfData pdfData = new ContractPdfData(
                ContractType.INITIAL, contractId,
                contract.getProject().getName(),
                contract.getProject().getRepoOwner(),
                contract.getProject().getRepoName(),
                contract.getCreatedAt(), LocalDateTime.now(), rows);

        byte[] pdfBytes = pdfService.generate(pdfData);
        String pdfUrl = storageService.uploadPdf(pdfBytes, contractId);
        contract.complete(pdfUrl);
        log.info("[Contract] INITIAL completed id={} → project ACTIVE", contractId);

        // 완료 이메일
        List<MemberSignatureStatus> statuses = getContract(contractId).members();
        signatures.forEach(sig -> {
            String email = sig.getUser().getEmail();
            if (email != null && !email.isBlank()) {
                emailService.sendContractCompleted(
                        email, sig.getUser().getUsername(),
                        contract.getProject().getName(), pdfUrl, statuses);
            }
        });
    }

    private void onFinalContractAllSigned(Contract contract) {
        Long contractId = contract.getId();

        List<EquitySnapshot> snapshots = snapshotRepository.findByContractId(contractId);
        List<Signature> signatures = signatureRepository.findByContractId(contractId);

        Map<Long, Signature> sigByUser = signatures.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        List<ContractPdfData.MemberPdfRow> rows = snapshots.stream()
                .map(snap -> {
                    Signature sig = sigByUser.get(snap.getUser().getId());
                    return new ContractPdfData.MemberPdfRow(
                            snap.getUser().getUsername(),
                            snap.getPercentage(),
                            snap.getRawScore(),
                            snap.getTotalCommits(), snap.getTotalPrs(),
                            snap.getTotalReviews(), snap.getTotalIssues(),
                            sig != null ? sig.getSignedAt() : null,
                            sig != null ? sig.getIpAddress() : null);
                })
                .sorted((a, b) -> Double.compare(
                        b.equity() != null ? b.equity() : 0.0,
                        a.equity() != null ? a.equity() : 0.0))
                .toList();

        ContractPdfData pdfData = new ContractPdfData(
                ContractType.FINAL, contractId,
                contract.getProject().getName(),
                contract.getProject().getRepoOwner(),
                contract.getProject().getRepoName(),
                contract.getCreatedAt(), LocalDateTime.now(), rows);

        byte[] pdfBytes = pdfService.generate(pdfData);
        String pdfUrl = storageService.uploadPdf(pdfBytes, contractId);
        contract.complete(pdfUrl);
        log.info("[Contract] FINAL completed id={}", contractId);

        List<MemberSignatureStatus> statuses = getContract(contractId).members();
        signatures.forEach(sig -> {
            String email = sig.getUser().getEmail();
            if (email != null && !email.isBlank()) {
                emailService.sendContractCompleted(
                        email, sig.getUser().getUsername(),
                        contract.getProject().getName(), pdfUrl, statuses);
            }
        });
    }

    // ── 계약 삭제 ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteContract(Long contractId, Long requestUserId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStatus() == ContractStatus.COMPLETED) {
            throw new CustomException(ErrorCode.CONTRACT_NOT_DELETABLE);
        }

        requireOwner(contract.getProject().getId(), requestUserId);

        snapshotRepository.findByContractId(contractId).forEach(EquitySnapshot::unlinkContract);
        signatureRepository.deleteAll(signatureRepository.findByContractId(contractId));
        contractRepository.delete(contract);
        log.info("[Contract] deleted id={}", contractId);
    }

    // ── 목록 조회 ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContractResponse> listContracts(Long projectId) {
        return contractRepository.findByProjectId(projectId).stream()
                .map(ContractResponse::from)
                .toList();
    }

    // ── PDF 다운로드 ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PdfDownloadResult downloadPdf(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStatus() == ContractStatus.COMPLETED && contract.getPdfUrl() != null) {
            return PdfDownloadResult.redirect(contract.getPdfUrl());
        }

        ContractType type = contract.getContractType();
        List<Signature> signatures = signatureRepository.findByContractId(contractId);
        Map<Long, Signature> sigByUser = signatures.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        List<ContractPdfData.MemberPdfRow> rows;

        if (type == ContractType.INITIAL) {
            rows = projectMemberRepository.findByProjectId(contract.getProject().getId()).stream()
                    .map(m -> {
                        Signature sig = sigByUser.get(m.getUser().getId());
                        return new ContractPdfData.MemberPdfRow(
                                m.getUser().getUsername(),
                                null,
                                0.0, 0, 0, 0, 0,
                                sig != null ? sig.getSignedAt() : null,
                                sig != null ? sig.getIpAddress() : null);
                    })
                    .sorted((a, b) -> a.username().compareTo(b.username()))
                    .toList();
        } else {
            rows = snapshotRepository.findByContractId(contractId).stream()
                    .map(snap -> {
                        Signature sig = sigByUser.get(snap.getUser().getId());
                        return new ContractPdfData.MemberPdfRow(
                                snap.getUser().getUsername(),
                                snap.getPercentage(),
                                snap.getRawScore(),
                                snap.getTotalCommits(), snap.getTotalPrs(),
                                snap.getTotalReviews(), snap.getTotalIssues(),
                                sig != null ? sig.getSignedAt() : null,
                                sig != null ? sig.getIpAddress() : null);
                    })
                    .sorted((a, b) -> Double.compare(
                            b.equity() != null ? b.equity() : 0.0,
                            a.equity() != null ? a.equity() : 0.0))
                    .toList();
        }

        ContractPdfData pdfData = new ContractPdfData(
                type, contractId,
                contract.getProject().getName(),
                contract.getProject().getRepoOwner(),
                contract.getProject().getRepoName(),
                contract.getCreatedAt(), null, rows);

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

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    private void requireOwner(Long projectId, Long userId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .filter(m -> m.getRole() == MemberRole.OWNER)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN));
    }
}
