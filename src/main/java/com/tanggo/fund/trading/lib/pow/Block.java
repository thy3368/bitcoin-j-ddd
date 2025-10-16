package com.tanggo.fund.trading.lib.pow;

import lombok.Data;

@Data
public class Block {
    private final int index;
    private final long timestamp;
    private final String data;
    private final String previousHash;
    private final int difficulty;
    private String hash;
    private int nonce;

    public Block(int index, String data, String previousHash, int difficulty) {
        this.index = index;
        this.timestamp = System.currentTimeMillis();
        this.data = data;
        this.previousHash = previousHash;
        this.difficulty = difficulty;
        this.hash = calculateHash(); // 初始计算哈希
    }

    public String calculateHash() {
        String input = index + timestamp + data + previousHash + nonce + difficulty;
        return SHA256(input); // SHA-256哈希计算
    }

    private String SHA256(String input) {

        return StringUtil.applySha256(input);
    }

    public void mineBlock() {
        String target = new String(new char[difficulty]).replace('\0', '0'); // 目标字符串（如"0000"）
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateHash(); // 重新计算哈希
        }
        System.out.println("Block mined! Hash: " + hash);
    }
}
