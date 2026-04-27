package com.equicode.gitequity.user;

import com.equicode.gitequity.auth.UserPrincipal;
import com.equicode.gitequity.common.response.ApiResponse;
import com.equicode.gitequity.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(userService.getUser(principal.getId()));
    }
}
