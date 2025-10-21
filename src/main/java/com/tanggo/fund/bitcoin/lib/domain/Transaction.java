package com.tanggo.fund.bitcoin.lib.domain;

import lombok.Data;

import java.math.BigInteger;

@Data
public class Transaction {
    String from;
    String to;
    BigInteger nonce;
    String hash;
    BigInteger gasPrice;


}
