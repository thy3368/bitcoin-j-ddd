package com.tanggo.fund.raft.domain;

import lombok.Data;

@Data
public class PeerRaftNode {

    private String nodeId;
    private String remoteNodeId;


    //远程信息
    //node id等
}
