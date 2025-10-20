package com.tanggo.fund.raft.inbound;

import com.tanggo.fund.raft.service.LogEntryService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 集群测试演示
public class RaftClusterDemo {
    public static void main(String[] args) throws InterruptedException {
        // 创建3节点集群
        Map<String, LogEntryService> nodes = new ConcurrentHashMap<>();

        LogEntryService node1 = new LogEntryService("node1", nodes);
        LogEntryService node2 = new LogEntryService("node2", nodes);
        LogEntryService node3 = new LogEntryService("node3", nodes);

        nodes.put("node1", node1);
        nodes.put("node2", node2);
        nodes.put("node3", node3);

        // 等待选举完成
        Thread.sleep(1000);

        // 模拟客户端请求
        System.out.println("=== 测试日志复制流程 ===");

        // 查找领导者并发送请求
        for (LogEntryService node : nodes.values()) {
            if (node.handleClientCommand("SET key1 value1")) {
                break;
            }
        }

        Thread.sleep(500);

        // 发送第二个请求
        for (LogEntryService node : nodes.values()) {
            if (node.handleClientCommand("DELETE key2")) {
                break;
            }
        }

        Thread.sleep(1000);

        // 打印所有节点日志状态
        for (LogEntryService node : nodes.values()) {
            node.printLog();
        }
    }
}
