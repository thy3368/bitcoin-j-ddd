package com.tanggo.fund.bitcoinjddd.lib.inboud;


import com.tanggo.fund.bitcoinjddd.lib.domain.Command;
import com.tanggo.fund.bitcoinjddd.lib.service.TransactionApps;

public class TransactionInbound {

    private TransactionApps transactionApps;

    void handle(Command command) {

        transactionApps.createTransaction();

    }


}
