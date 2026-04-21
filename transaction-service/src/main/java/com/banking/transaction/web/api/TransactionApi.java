package com.banking.transaction.web.api;

import com.banking.common.AppConstants;
import com.banking.common.web.dto.ErrorResponse;
import com.banking.transaction.web.dto.TransactionResponse;
import com.banking.transaction.web.dto.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Transactions", description = "Money transfers and transaction history")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/transactions")
public interface TransactionApi {

    // --- Transfers ---

    @Operation(summary = "Transfer money", description = "Initiates a money transfer. Idempotent via X-Idempotency-Key header.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Transfer accepted, processing asynchronously"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate idempotency key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/transfer", consumes = AppConstants.MediaTypes.JSON, produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<TransactionResponse> transfer(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @Parameter(description = "Unique idempotency key (UUID or any string, max 64 chars)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @RequestHeader(AppConstants.Headers.IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody TransferRequest request
    );

    // --- History ---

    @Operation(summary = "Get my transactions", description = "Returns paginated transaction history for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Transaction list")
    @GetMapping(produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<Page<TransactionResponse>> getMyTransactions(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @Parameter(hidden = true) Pageable pageable
    );

    @Operation(summary = "Get transaction by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/{transactionId}", produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<TransactionResponse> getTransaction(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @Parameter(description = "Transaction ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID transactionId
    );
}
