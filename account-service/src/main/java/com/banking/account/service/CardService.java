package com.banking.account.service;

import com.banking.account.domain.Account;
import com.banking.account.domain.AccountStatus;
import com.banking.account.domain.Card;
import com.banking.account.domain.CardStatus;
import com.banking.account.exception.AccountException;
import com.banking.account.repository.AccountRepository;
import com.banking.account.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public Card issueCard(UUID accountId, UUID userId, BigDecimal transactionLimit) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountException.notFound("Account not found"));
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw AccountException.badRequest("Cannot issue card for a closed account");
        }
        Card card = Card.builder()
                .account(account)
                .cardNumber(generateCardNumber())
                .transactionLimit(transactionLimit)
                .build();
        return cardRepository.save(card);
    }

    @Transactional(readOnly = true)
    public List<Card> getCards(UUID accountId, UUID userId) {
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountException.notFound("Account not found"));
        return cardRepository.findAllByAccountId(accountId);
    }

    @Transactional
    public Card blockCard(UUID accountId, UUID cardId, UUID userId) {
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountException.notFound("Account not found"));
        Card card = cardRepository.findByIdAndAccountId(cardId, accountId)
                .orElseThrow(() -> AccountException.notFound("Card not found"));
        if (card.getStatus() == CardStatus.BLOCKED) {
            throw AccountException.conflict("Card is already blocked");
        }
        card.setStatus(CardStatus.BLOCKED);
        return cardRepository.save(card);
    }

    @Transactional
    public Card setLimit(UUID accountId, UUID cardId, UUID userId, BigDecimal limit) {
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountException.notFound("Account not found"));
        Card card = cardRepository.findByIdAndAccountId(cardId, accountId)
                .orElseThrow(() -> AccountException.notFound("Card not found"));
        card.setTransactionLimit(limit);
        return cardRepository.save(card);
    }

    private String generateCardNumber() {
        long number = ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L, 9_999_999_999_999_999L);
        String digits = String.valueOf(number);
        return digits.substring(0, 4) + " " + digits.substring(4, 8) + " " +
               digits.substring(8, 12) + " " + digits.substring(12, 16);
    }
}
