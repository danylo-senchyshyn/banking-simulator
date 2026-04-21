package com.banking.account.web.api;

import com.banking.account.web.dto.AccountResponse;
import com.banking.account.web.dto.CreateAccountRequest;
import com.banking.account.web.dto.DepositRequest;
import com.banking.common.AppConstants;
import com.banking.common.web.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Tag(name = "Accounts", description = "Account management")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/accounts")
public interface AccountApi {

    // --- Account CRUD ---

    @Operation(summary = "Create account", description = "Creates a new account for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = AppConstants.MediaTypes.JSON, produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<AccountResponse> createAccount(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @Valid @RequestBody CreateAccountRequest request
    );

    @Operation(summary = "Get my accounts", description = "Returns all accounts owned by the authenticated user")
    @ApiResponse(responseCode = "200", description = "List of accounts")
    @GetMapping(produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<List<AccountResponse>> getMyAccounts(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId
    );

    @Operation(summary = "Get account by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/{accountId}", produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<AccountResponse> getAccount(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @Parameter(description = "Account ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID accountId
    );

    @Operation(summary = "Get account balance", description = "Returns cached balance from Redis")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance returned"),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/{accountId}/balance", produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<BigDecimal> getBalance(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @PathVariable UUID accountId
    );

    @Operation(summary = "Deposit funds", description = "Adds funds to the account balance. Supply X-Idempotency-Key to prevent duplicate deposits.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit successful"),
            @ApiResponse(responseCode = "400", description = "Invalid amount",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Account is closed or duplicate request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/{accountId}/deposit", consumes = AppConstants.MediaTypes.JSON, produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<AccountResponse> deposit(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @Parameter(description = "Account ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID accountId,
            @Valid @RequestBody DepositRequest request,
            @Parameter(description = "Optional idempotency key — duplicate requests within 24 h return 409")
            @RequestHeader(value = AppConstants.Headers.IDEMPOTENCY_KEY, required = false) String idempotencyKey
    );

    @Operation(summary = "Close account", description = "Closes account if balance is zero")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account closed"),
            @ApiResponse(responseCode = "409", description = "Account has non-zero balance or already closed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping(value = "/{accountId}", produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<AccountResponse> closeAccount(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @PathVariable UUID accountId
    );
}
