package com.tanggo.fund.bitcoinjddd.lib.service;


import com.tanggo.fund.bitcoinjddd.lib.domain.Command;
import com.tanggo.fund.bitcoinjddd.lib.domain.Peer;
import com.tanggo.fund.bitcoinjddd.lib.domain.Transaction;
import com.tanggo.fund.bitcoinjddd.lib.domain.gateway.PeerGateway;
import com.tanggo.fund.bitcoinjddd.lib.domain.repo.TransactionRepo;

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
