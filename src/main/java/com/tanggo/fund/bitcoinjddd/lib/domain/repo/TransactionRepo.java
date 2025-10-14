package com.tanggo.fund.bitcoinjddd.lib.domain.repo;

import com.tanggo.fund.bitcoinjddd.lib.domain.Transaction;

public interface TransactionRepo {
    void insert(Transaction transaction);
}
