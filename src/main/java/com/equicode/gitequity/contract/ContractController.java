package com.equicode.gitequity.contract;

import com.equicode.gitequity.auth.UserPrincipal;
import com.equicode.gitequity.common.response.ApiResponse;
import com.equicode.gitequity.contract.ContractService.PdfDownloadResult;
import com.equicode.gitequity.contract.dto.ContractDetailResponse;
import com.equicode.gitequity.contract.dto.ContractResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    // ── Phase A ───────────────────────────────────────────────────────────────

    @PostMapping("/api/projects/{projectId}/contracts")
    public ApiResponse<ContractResponse> create(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        ContractResponse response = contractService.createContract(projectId, principal.getId());
        return ApiResponse.ok("contract created", response);
    }

    @GetMapping("/api/projects/{projectId}/contracts")
    public ApiResponse<List<ContractResponse>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(contractService.listContracts(projectId));
    }

    @GetMapping("/api/contracts/{contractId}")
    public ApiResponse<ContractDetailResponse> get(@PathVariable Long contractId) {
        return ApiResponse.ok(contractService.getContract(contractId));
    }

    @PostMapping("/api/contracts/{contractId}/send")
    public ApiResponse<ContractResponse> send(
            @PathVariable Long contractId,
            @AuthenticationPrincipal UserPrincipal principal) {
        ContractResponse response = contractService.sendContract(contractId, principal.getId());
        return ApiResponse.ok("contract sent to all members", response);
    }

    // ── Phase B ───────────────────────────────────────────────────────────────

    @PostMapping("/api/contracts/{contractId}/sign")
    public ApiResponse<ContractDetailResponse> sign(
            @PathVariable Long contractId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {
        String ip = resolveClientIp(request);
        ContractDetailResponse detail = contractService.sign(contractId, principal.getId(), ip);
        return ApiResponse.ok("signed successfully", detail);
    }

    // ── 계약 삭제 ────────────────────────────────────────────────────────────

    @DeleteMapping("/api/contracts/{contractId}")
    public ApiResponse<Void> delete(
            @PathVariable Long contractId,
            @AuthenticationPrincipal UserPrincipal principal) {
        contractService.deleteContract(contractId, principal.getId());
        return ApiResponse.ok("contract deleted", null);
    }

    // ── Phase F: PDF 다운로드 ─────────────────────────────────────────────────

    @GetMapping("/api/contracts/{contractId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long contractId) {
        PdfDownloadResult result = contractService.downloadPdf(contractId);

        if (result.isRedirect()) {
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, result.url())
                    .build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + result.filename() + "\"")
                .body(result.bytes());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
