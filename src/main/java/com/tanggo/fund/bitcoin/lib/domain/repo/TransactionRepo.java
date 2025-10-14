package com.tanggo.fund.bitcoin.lib.domain.repo;

import com.tanggo.fund.bitcoin.lib.domain.Transaction;

public interface TransactionRepo {
    void insert(Transaction transaction);
}
