package com.tanggo.fund.eth.lib.domain;

import lombok.Data;

@Data
public class Block {
    private Header header;
    private BlockBody blockBody;

}
