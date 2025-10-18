package com.tanggo.fund.eth.lib.service;

import com.tanggo.fund.eth.lib.domain.Account;
import com.tanggo.fund.eth.lib.domain.Block;
import com.tanggo.fund.eth.lib.domain.Receipt;
import com.tanggo.fund.eth.lib.domain.transaction.Eip1559Transaction;
import com.tanggo.fund.eth.lib.outbound.AccountRepo;
import com.tanggo.fund.eth.lib.outbound.ReceiptRepo;
import com.tanggo.fund.eth.lib.outbound.TxPoolRepo;

public class TransactionService {

    private TxPoolRepo txPoolRepo;
    private AccountRepo accountRepo;
    private ReceiptRepo receiptRepo;

    void create() {

        Eip1559Transaction eip1559Transaction = new Eip1559Transaction();
        eip1559Transaction.signature();
        txPoolRepo.add(eip1559Transaction);

    }


    void statelessValidation() {

        Eip1559Transaction transaction = txPoolRepo.query();
        Account sender;
        Account recipient;

    }

    void StatefulValidation() {

        Eip1559Transaction transaction = txPoolRepo.query();
        Account sender = accountRepo.query("sender");
        Account recipient = accountRepo.query("recipient");

    }


    void execute() {

        Eip1559Transaction transaction = txPoolRepo.query();
        Account sender = accountRepo.query("sender");
        sender.decute(2 + 2);
        Account recipient = accountRepo.query("recipient");
        Receipt receipt = null;
        accountRepo.update(sender);
        accountRepo.update(recipient);
        receiptRepo.save(receipt);
        Block block = new Block();
        block.getBlockBody().getTransactions().add(transaction);

        //todo execute

    }


}
