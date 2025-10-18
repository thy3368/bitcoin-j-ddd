package com.tanggo.fund.eth.lib.domain;

import lombok.Data;

@Data
public class Account {

    private int nonce;
    private int balance;
    private String bytecode_hash;

    public void decute(int i) {
    }
}
