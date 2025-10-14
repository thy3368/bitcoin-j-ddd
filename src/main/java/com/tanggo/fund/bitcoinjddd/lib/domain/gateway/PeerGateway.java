package com.tanggo.fund.bitcoinjddd.lib.domain.gateway;

import com.tanggo.fund.bitcoinjddd.lib.domain.Command;
import com.tanggo.fund.bitcoinjddd.lib.domain.Peer;

public interface PeerGateway {
    void sendCommand(Peer peer, Command command);
}
