package com.banking.account.mapper;

import com.banking.account.domain.Account;
import com.banking.account.web.dto.AccountResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountResponse toResponse(Account account);
}
