package com.banking.auth.web.api;

import com.banking.auth.domain.User;
import com.banking.auth.web.dto.AuthResponse;
import com.banking.common.AppConstants;
import com.banking.common.web.dto.ErrorResponse;
import com.banking.auth.web.dto.LoginRequest;
import com.banking.auth.web.dto.RefreshRequest;
import com.banking.auth.web.dto.RegisterRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Auth", description = "Registration, login, token refresh and logout")
@RequestMapping("/api/v1/auth")
public interface AuthApi {

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Register a new user",
            description = """
                    Creates a new user account with the USER role.

                    On success, returns a pair of tokens:
                    - **accessToken** — short-lived JWT (15 min), use in Authorization header
                    - **refreshToken** — long-lived opaque token (7 days), use to obtain new access tokens

                    Email must be unique across the system.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error — missing or invalid fields",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered or duplicate request",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AuthResponse> register(
            @RequestBody(description = "User registration data", required = true,
                    content = @Content(schema = @Schema(implementation = RegisterRequest.class)))
            @Valid @org.springframework.web.bind.annotation.RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            @Parameter(description = "Optional idempotency key — duplicate requests within 24 h return 409")
            @RequestHeader(value = AppConstants.Headers.IDEMPOTENCY_KEY, required = false) String idempotencyKey);

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Login with email and password",
            description = """
                    Authenticates a user and returns a token pair.

                    Failed login attempts are tracked per IP:
                    - After **5 failed attempts**, the IP is blocked for **15 minutes**
                    - A successful login resets the counter

                    Blocked accounts cannot login regardless of credentials.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid email or password",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Account is blocked",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many failed attempts — IP blocked for 15 minutes",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AuthResponse> login(
            @RequestBody(description = "Login credentials", required = true,
                    content = @Content(schema = @Schema(implementation = LoginRequest.class)))
            @Valid @org.springframework.web.bind.annotation.RequestBody LoginRequest request,
            HttpServletRequest httpRequest);

    // -------------------------------------------------------------------------
    // Token refresh
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Refresh access token",
            description = """
                    Exchanges a valid refresh token for a new token pair.

                    Refresh tokens are **single-use** — each call invalidates the old token
                    and issues a new one. This prevents replay attacks.

                    If the refresh token is expired or already used, returns 401.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token is invalid or expired",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/refresh",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AuthResponse> refresh(
            @RequestBody(description = "Refresh token", required = true,
                    content = @Content(schema = @Schema(implementation = RefreshRequest.class)))
            @Valid @org.springframework.web.bind.annotation.RequestBody RefreshRequest request);

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Logout",
            description = """
                    Invalidates **all** refresh tokens for the current user.

                    If the user is logged in from multiple devices, all sessions are terminated.
                    The access token remains valid until it expires naturally (max 15 min).
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out — all sessions terminated", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) @AuthenticationPrincipal User user);
}
