package com.tanggo.fund.bitcoinjddd.lib.domain;

import lombok.Data;

import java.net.InetAddress;

@Data
public class PeerAddress {
    private final InetAddress addr;   // Used for IPV4, IPV6, null otherwise
    private final String hostname;    // Used for (.onion addresses) TORV2, TORV3, null otherwise
    private final int port;

    public PeerAddress(InetAddress addr, String hostname, int port) {
        this.addr = addr;
        this.hostname = hostname;
        this.port = port;
    }
}
