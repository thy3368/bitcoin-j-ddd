package com.tanggo.fund.eth.lib.domain.repo;

import com.tanggo.fund.bitcoin.lib.domain.Transaction;

import java.util.List;

public interface ITransactionPool {
    boolean addTransaction(Transaction tx);

    default void removeTransactionFromGlobalIndices(Transaction existingTx) {
    }

    List<Transaction> getPendingTransactions(int limit);

    void removeTransaction(String txHash);
}
