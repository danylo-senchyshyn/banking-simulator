package com.banking.auth.web.api;

import com.banking.auth.domain.User;
import com.banking.auth.web.dto.ChangePasswordRequest;
import com.banking.common.web.dto.ErrorResponse;
import com.banking.auth.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "User", description = "Current user profile and password management")
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
public interface UserApi {

    // -------------------------------------------------------------------------
    // Profile
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get current user profile",
            description = """
                    Returns the profile of the currently authenticated user.

                    The user is identified by the JWT access token in the Authorization header.
                    No additional parameters required.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserResponse> getProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal User user);

    // -------------------------------------------------------------------------
    // Password management
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Change current user password",
            description = """
                    Changes the password for the currently authenticated user.

                    Requires the current password for verification before applying the change.
                    After a successful change, existing sessions remain valid — refresh tokens
                    are NOT invalidated automatically.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Current password is incorrect",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/me/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> changePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal User user,
            @RequestBody(description = "Current and new password", required = true,
                    content = @Content(schema = @Schema(implementation = ChangePasswordRequest.class)))
            @Valid @org.springframework.web.bind.annotation.RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest);
}
