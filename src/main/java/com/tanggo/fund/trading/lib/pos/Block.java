package com.tanggo.fund.trading.lib.pos;

public class Block {
    private final String previousHash;
    private final String hash;
    private final String data;
    private final long timeStamp;
    private final String validator; // 验证者地址

    public Block(String data, String previousHash, String validator) {
        this.data = data;
        this.previousHash = previousHash;
        this.timeStamp = System.currentTimeMillis();
        this.validator = validator;
        this.hash = calculateHash(); // 计算哈希（SHA-256）
    }

    private String calculateHash() {
        return null;
    }

    public String getHash() {
        return hash;
    }
}
