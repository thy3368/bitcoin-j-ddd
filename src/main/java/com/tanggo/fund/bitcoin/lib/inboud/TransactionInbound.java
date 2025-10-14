package com.tanggo.fund.bitcoin.lib.inboud;


import com.tanggo.fund.bitcoin.lib.domain.Command;
import com.tanggo.fund.bitcoin.lib.service.TransactionApps;

public class TransactionInbound {

    private TransactionApps transactionApps;

    void handle(Command command) {

        transactionApps.createTransaction();

    }


}
