package com.tanggo.fund.eth.lib.domain.repo;

import com.tanggo.fund.eth.lib.domain.Account;

public interface IAccountRepo {
    Account query(String address);

    void update(Account account);
}
