package com.tanggo.fund.bitcoinjddd.lib.service;


import com.tanggo.fund.bitcoinjddd.lib.domain.Command;

public class CommonApps {

    private TransactionApps transactionApps;

    void handle(Command command) {

        transactionApps.createTransaction();

    }


}
