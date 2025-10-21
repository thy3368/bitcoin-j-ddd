package com.tanggo.fund.raft.example;

import com.tanggo.fund.raft.service.ILogEntryService;
import com.tanggo.fund.raft.service.command.AppendEntriesCommand;
import com.tanggo.fund.raft.service.command.AppendEntriesResponse;
import com.tanggo.fund.raft.service.command.impl.ProxyLogEntryService;

/**
 * Raft JSON-RPC 使用示例
 * 演示如何使用 ProxyLogEntryService 与远程 Raft 节点通信
 */
public class RaftRpcExample {

    public static void main(String[] args) {
        // 示例 1: 创建代理服务，连接到节点1
        String node1Url = "http://localhost:8545/raft/rpc";
        ILogEntryService node1Proxy = new ProxyLogEntryService(node1Url);

        // 示例 2: 处理客户端命令
        boolean success = node1Proxy.handleClientCommand("SET key1 value1");
        System.out.println("命令处理结果: " + success);

        // 示例 3: 发送追加日志请求（心跳）
        AppendEntriesCommand heartbeat = new AppendEntriesCommand(
                1,      // term
                0,      // prevLogIndex
                0,      // prevLogTerm
                null,   // entries (null 表示心跳)
                0       // leaderCommit
        );
        AppendEntriesResponse response = node1Proxy.handleAppendEntries(heartbeat);
        System.out.println("追加日志响应: term=" + response.getTerm() +
                ", success=" + response.isSuccess() +
                ", matchIndex=" + response.getMatchIndex());

        // 示例 4: 连接到多个节点
        String node2Url = "http://localhost:8546/raft/rpc";
        String node3Url = "http://localhost:8547/raft/rpc";

        ILogEntryService node2Proxy = new ProxyLogEntryService(node2Url);
        ILogEntryService node3Proxy = new ProxyLogEntryService(node3Url);

        // 向所有节点发送心跳
        AppendEntriesCommand heartbeatCmd = new AppendEntriesCommand(1, 0, 0, null, 0);

        AppendEntriesResponse resp1 = node1Proxy.handleAppendEntries(heartbeatCmd);
        AppendEntriesResponse resp2 = node2Proxy.handleAppendEntries(heartbeatCmd);
        AppendEntriesResponse resp3 = node3Proxy.handleAppendEntries(heartbeatCmd);

        System.out.println("Node1 响应: " + resp1.isSuccess());
        System.out.println("Node2 响应: " + resp2.isSuccess());
        System.out.println("Node3 响应: " + resp3.isSuccess());

        // 示例 5: 在 Spring Boot 应用中使用 (通过依赖注入)
        /*
        @Service
        public class RaftClusterService {
            private final List<ILogEntryService> clusterNodes;

            public RaftClusterService() {
                this.clusterNodes = List.of(
                    new ProxyLogEntryService("http://node1:8545/raft/rpc"),
                    new ProxyLogEntryService("http://node2:8546/raft/rpc"),
                    new ProxyLogEntryService("http://node3:8547/raft/rpc")
                );
            }

            public void sendHeartbeatToAll() {
                AppendEntriesCommand heartbeat = new AppendEntriesCommand(1, 0, 0, null, 0);
                clusterNodes.forEach(node -> {
                    try {
                        node.handleAppendEntries(heartbeat);
                    } catch (Exception e) {
                        // 处理节点不可达的情况
                    }
                });
            }
        }
        */
    }
}
