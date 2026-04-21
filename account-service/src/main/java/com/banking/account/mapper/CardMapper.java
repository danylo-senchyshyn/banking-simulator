package com.banking.account.mapper;

import com.banking.account.domain.Card;
import com.banking.account.web.dto.CardResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CardMapper {

    @Mapping(source = "account.id", target = "accountId")
    CardResponse toResponse(Card card);
}
