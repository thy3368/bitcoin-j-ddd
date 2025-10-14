package com.tanggo.fund.bitcoin.lib.service;


import com.tanggo.fund.bitcoin.lib.domain.Command;

public class CommonApps {

    private TransactionApps transactionApps;

    void handle(Command command) {

        transactionApps.createTransaction();

    }


}
