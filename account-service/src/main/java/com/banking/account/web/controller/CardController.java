package com.banking.account.web.controller;

import com.banking.account.mapper.CardMapper;
import com.banking.account.service.CardService;
import com.banking.account.web.api.CardApi;
import com.banking.account.web.dto.CardResponse;
import com.banking.account.web.dto.IssueCardRequest;
import com.banking.account.web.dto.SetLimitRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CardController implements CardApi {

    private final CardService cardService;
    private final CardMapper cardMapper;

    @Override
    public ResponseEntity<CardResponse> issueCard(String userId, UUID accountId, IssueCardRequest request) {
        var card = cardService.issueCard(accountId, UUID.fromString(userId), request.transactionLimit());
        return ResponseEntity.status(HttpStatus.CREATED).body(cardMapper.toResponse(card));
    }

    @Override
    public ResponseEntity<List<CardResponse>> getCards(String userId, UUID accountId) {
        var cards = cardService.getCards(accountId, UUID.fromString(userId))
                .stream().map(cardMapper::toResponse).toList();
        return ResponseEntity.ok(cards);
    }

    @Override
    public ResponseEntity<CardResponse> blockCard(String userId, UUID accountId, UUID cardId) {
        var card = cardService.blockCard(accountId, cardId, UUID.fromString(userId));
        return ResponseEntity.ok(cardMapper.toResponse(card));
    }

    @Override
    public ResponseEntity<CardResponse> setLimit(String userId, UUID accountId, UUID cardId, SetLimitRequest request) {
        var card = cardService.setLimit(accountId, cardId, UUID.fromString(userId), request.transactionLimit());
        return ResponseEntity.ok(cardMapper.toResponse(card));
    }
}
