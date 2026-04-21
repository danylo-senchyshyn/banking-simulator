package com.banking.auth.web.api;

import com.banking.auth.domain.User;
import com.banking.auth.web.dto.ChangeRoleRequest;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin", description = "User management operations — requires ADMIN role")
@RequestMapping("/api/v1/admin")
@SecurityRequirement(name = "bearerAuth")
public interface AdminApi {

    // -------------------------------------------------------------------------
    // User listing
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get all users",
            description = """
                    Returns a list of all registered users in the system.

                    Available to ADMIN role only. Includes blocked users.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User list returned",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions — ADMIN role required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<UserResponse>> getAllUsers();

    // -------------------------------------------------------------------------
    // Block / unblock
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Block or unblock a user",
            description = """
                    Sets the blocked status of a user.

                    - **blocked=true** — user cannot login, active sessions remain until token expiry
                    - **blocked=false** — user can login again

                    The action is recorded in the audit log with the admin's identity.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Block status updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions — ADMIN role required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/users/{id}/block", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserResponse> blockUser(
            @Parameter(description = "UUID of the user to block or unblock", required = true,
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Parameter(description = "true to block the user, false to unblock", required = true,
                    example = "true")
            @RequestParam boolean blocked,
            HttpServletRequest httpRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal User admin);

    // -------------------------------------------------------------------------
    // Role management
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Change user role",
            description = """
                    Updates the role of a user.

                    Available roles: **USER**, **ADMIN**.

                    Changing a role to ADMIN grants full administrative access immediately —
                    the change takes effect on the user's next authenticated request.
                    The action is recorded in the audit log with before/after values.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid role value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions — ADMIN role required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/users/{id}/role",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserResponse> changeRole(
            @Parameter(description = "UUID of the user", required = true,
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @RequestBody(description = "New role to assign", required = true,
                    content = @Content(schema = @Schema(implementation = ChangeRoleRequest.class)))
            @Valid @org.springframework.web.bind.annotation.RequestBody ChangeRoleRequest request,
            HttpServletRequest httpRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal User admin);
}
