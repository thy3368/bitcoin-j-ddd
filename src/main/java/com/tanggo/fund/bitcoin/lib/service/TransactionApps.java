package com.tanggo.fund.bitcoin.lib.service;


import com.tanggo.fund.bitcoin.lib.domain.Command;
import com.tanggo.fund.bitcoin.lib.domain.Peer;
import com.tanggo.fund.bitcoin.lib.domain.Transaction;
import com.tanggo.fund.bitcoin.lib.domain.gateway.PeerGateway;
import com.tanggo.fund.bitcoin.lib.domain.repo.TransactionRepo;

public class TransactionApps {
    private TransactionRepo transactionRepo;

    private PeerGateway peerGateway;


    public void createTransaction() {

        Transaction transaction = new Transaction();

        transactionRepo.insert(transaction);

        Command command = new Command();
        Peer peer = new Peer();
        peerGateway.sendCommand(peer, command);


    }


}
