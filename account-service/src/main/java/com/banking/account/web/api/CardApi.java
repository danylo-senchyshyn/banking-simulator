package com.banking.account.web.api;

import com.banking.account.web.dto.CardResponse;
import com.banking.account.web.dto.IssueCardRequest;
import com.banking.account.web.dto.SetLimitRequest;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "Cards", description = "Card management")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/accounts/{accountId}/cards")
public interface CardApi {

    // --- Card operations ---

    @Operation(summary = "Issue card", description = "Issues a new card for the given account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Card issued"),
            @ApiResponse(responseCode = "400", description = "Account is closed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = AppConstants.MediaTypes.JSON, produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<CardResponse> issueCard(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @PathVariable UUID accountId,
            @Valid @RequestBody IssueCardRequest request
    );

    @Operation(summary = "Get cards", description = "Returns all cards for the given account")
    @ApiResponse(responseCode = "200", description = "List of cards")
    @GetMapping(produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<List<CardResponse>> getCards(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @PathVariable UUID accountId
    );

    @Operation(summary = "Block card")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Card blocked"),
            @ApiResponse(responseCode = "409", description = "Card already blocked",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Card or account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/{cardId}/block", produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<CardResponse> blockCard(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @PathVariable UUID accountId,
            @PathVariable UUID cardId
    );

    @Operation(summary = "Set transaction limit", description = "Updates the per-transaction spending limit for a card")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Limit updated"),
            @ApiResponse(responseCode = "404", description = "Card or account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping(value = "/{cardId}/limit", consumes = AppConstants.MediaTypes.JSON, produces = AppConstants.MediaTypes.JSON)
    ResponseEntity<CardResponse> setLimit(
            @Parameter(hidden = true) @RequestHeader(AppConstants.Headers.USER_ID) String userId,
            @PathVariable UUID accountId,
            @PathVariable UUID cardId,
            @Valid @RequestBody SetLimitRequest request
    );
}
