package com.tanggo.fund.eth.lib.domain;

import com.tanggo.fund.eth.lib.domain.transaction.Eip1559Transaction;
import lombok.Data;

import java.util.List;

@Data
public class BlockBody {
    private List<Eip1559Transaction> transactions;
    private List<Header> headers;

}
