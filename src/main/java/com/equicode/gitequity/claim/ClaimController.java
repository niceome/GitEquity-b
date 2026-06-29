package com.equicode.gitequity.claim;

import com.equicode.gitequity.auth.UserPrincipal;
import com.equicode.gitequity.claim.dto.ClaimRequest;
import com.equicode.gitequity.claim.dto.ClaimResponse;
import com.equicode.gitequity.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @PostMapping("/api/projects/{projectId}/claims")
    public ApiResponse<ClaimResponse> create(
            @PathVariable Long projectId,
            @RequestBody ClaimRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClaimResponse response = claimService.createClaim(projectId, principal.getId(), request);
        return ApiResponse.ok("claim created", response);
    }

    @GetMapping("/api/projects/{projectId}/claims/pending-for-me")
    public ApiResponse<List<ClaimResponse>> pendingForMe(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(claimService.getPendingForMe(projectId, principal.getId()));
    }

    @PatchMapping("/api/claims/{id}/confirm")
    public ApiResponse<ClaimResponse> confirm(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClaimResponse response = claimService.confirmClaim(id, principal.getId());
        return ApiResponse.ok("claim confirmed", response);
    }

    @PatchMapping("/api/claims/{id}/reject")
    public ApiResponse<ClaimResponse> reject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClaimResponse response = claimService.rejectClaim(id, principal.getId());
        return ApiResponse.ok("claim rejected", response);
    }
}
