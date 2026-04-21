package com.banking.transaction.mapper;

import com.banking.transaction.domain.Transaction;
import com.banking.transaction.web.dto.TransactionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionResponse toResponse(Transaction transaction);
}
