package com.tanggo.fund.raft.inbound;


import com.tanggo.fund.raft.service.ILogEntryService;
import com.tanggo.fund.raft.service.command.impl.LogEntryService;
import com.tanggo.fund.raft.service.command.impl.ProxyLogEntryService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 集群测试演示 微服务布署
public class RaftClusterDemo2 {
    public static void main(String[] args) throws InterruptedException {
        // 创建3节点集群
        Map<String, ILogEntryService> nodes = new ConcurrentHashMap<>();


        ILogEntryService node1 = new ProxyLogEntryService("http://localhost:8545/raft/rpc");
        ILogEntryService node2 = new ProxyLogEntryService("http://localhost:8546/raft/rpc");
        ILogEntryService node3 = new ProxyLogEntryService("http://localhost:8547/raft/rpc");
        nodes.put("node1", node1);
        nodes.put("node2", node2);
        nodes.put("node3", node3);


        LogEntryService node = new LogEntryService("xxxx", nodes);


        // 等待选举完成
        Thread.sleep(1000);

        // 模拟客户端请求
        System.out.println("=== 测试日志复制流程 ===");

        // 查找领导者并发送请求
        node.handleClientCommand("SET key1 value1");
        node.handleClientCommand("DELETE key2");
        node.printLog();

        Thread.sleep(500);


    }
}
