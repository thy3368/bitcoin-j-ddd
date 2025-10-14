package com.tanggo.fund.bitcoin.lib.domain.gateway;

import com.tanggo.fund.bitcoin.lib.domain.Command;
import com.tanggo.fund.bitcoin.lib.domain.Peer;

public interface PeerGateway {
    void sendCommand(Peer peer, Command command);
}
