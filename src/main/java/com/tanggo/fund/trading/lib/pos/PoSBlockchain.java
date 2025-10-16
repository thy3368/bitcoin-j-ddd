package com.tanggo.fund.trading.lib.pos;

import java.util.*;

public class PoSBlockchain {
    private static final long REWARD = 10;
    private final List<Block> blockchain = new ArrayList<>();
    private final Map<String, Long> stakeHolders = new HashMap<>();

    // 初始化创世区块
    public PoSBlockchain() {
        Block genesis = new Block("Genesis", "0", "System");
        blockchain.add(genesis);
        stakeHolders.put("Validator1", 1000L);
        stakeHolders.put("Validator2", 2000L);
    }

    public static void main(String[] args) {
        PoSBlockchain chain = new PoSBlockchain();
        chain.addBlock("Transaction1");
        chain.addBlock("Transaction2");
        System.out.println("Blockchain: " + chain.blockchain);
    }

    // 选择验证者
    private String chooseValidator() {
        long totalStake = stakeHolders.values().stream().mapToLong(Long::longValue).sum();
        long randomStake = new Random().nextLong(totalStake);
        long accumulatedStake = 0;
        for (Map.Entry<String, Long> entry : stakeHolders.entrySet()) {
            accumulatedStake += entry.getValue();
            if (accumulatedStake >= randomStake) {
                return entry.getKey();
            }
        }
        return null;
    }

    // 添加新区块
    public void addBlock(String data) {
        String validator = chooseValidator();
        Block newBlock = new Block(data, getLatestBlock().getHash(), validator);
        blockchain.add(newBlock);
        stakeHolders.put(validator, stakeHolders.get(validator) + REWARD); // 发放奖励
    }

    private Block getLatestBlock() {
        return blockchain.get(blockchain.size() - 1);
    }
}
