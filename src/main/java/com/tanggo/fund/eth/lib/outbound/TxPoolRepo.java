package com.tanggo.fund.eth.lib.outbound;

import com.tanggo.fund.eth.lib.domain.transaction.Eip1559Transaction;

import java.util.ArrayList;
import java.util.List;

public class TxPoolRepo {
    private final List<Eip1559Transaction> eip1559Transactions = new ArrayList<>();

    public void add(Eip1559Transaction eip1559Transaction) {
        eip1559Transactions.add(eip1559Transaction);
    }

    public Eip1559Transaction query() {
        return eip1559Transactions.get(0);

    }
}
