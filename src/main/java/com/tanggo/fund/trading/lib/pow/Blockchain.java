package com.tanggo.fund.trading.lib.pow;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Blockchain {
    private List<Block> chain;
    private int difficulty;

    public Blockchain(int difficulty) {
        this.chain = new ArrayList<>();
        this.difficulty = difficulty;
        createGenesisBlock();
    }

    private void createGenesisBlock() {
        Block genesis = new Block(0, "Genesis Block", "0", difficulty);
        genesis.mineBlock();
        chain.add(genesis);
    }

    public void addBlock(String data) {
        Block lastBlock = chain.get(chain.size() - 1);
        Block newBlock = new Block(chain.size(), data, lastBlock.getHash(), difficulty);
        newBlock.mineBlock();
        chain.add(newBlock);
    }


}
