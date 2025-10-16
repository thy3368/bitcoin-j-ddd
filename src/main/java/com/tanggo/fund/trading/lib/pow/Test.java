package com.tanggo.fund.trading.lib.pow;

public class Test {
    public static void main(String[] args) {
        Blockchain blockchain = new Blockchain(3); // 难度=4
        System.out.println("Mining Block 1...");
        blockchain.addBlock("Send 1 BTC to Alice");
        System.out.println("Mining Block 2...");
        blockchain.addBlock("Send 2 BTC to Bob");

        // 打印区块链
        for (Block block : blockchain.getChain()) {
            System.out.println("Index: " + block.getIndex());
            System.out.println("Hash: " + block.getHash());
            System.out.println("Nonce: " + block.getNonce() + "\n");
        }
    }
}
