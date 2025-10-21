package com.tanggo.fund.raft.config;

import com.tanggo.fund.raft.service.ILogEntryService;
import com.tanggo.fund.raft.service.command.impl.LogEntryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Raft 配置类
 * 配置 Raft 服务的 Spring Bean
 */
@Configuration
public class RaftConfiguration {

    /**
     * 创建默认的 LogEntryService Bean
     * 如果没有其他实现，则使用这个默认实现
     */
    @Bean
    @ConditionalOnMissingBean(ILogEntryService.class)
    public ILogEntryService logEntryService() {
        // 创建单节点的 Raft 服务（用于测试）
        String nodeId = "node1";
        Map<String, ILogEntryService> nodes = new HashMap<>();

        LogEntryService service = new LogEntryService(nodeId, nodes);

        // 将自己加入节点列表
        nodes.put(nodeId, service);

        return service;
    }

    /**
     * TODO: 在实际集群部署中，需要配置多个节点
     * 示例配置：
     *
     * @Bean
     * public Map<String, ILogEntryService> raftClusterNodes(
     *     @Value("${raft.cluster.node1.url}") String node1Url,
     *     @Value("${raft.cluster.node2.url}") String node2Url,
     *     @Value("${raft.cluster.node3.url}") String node3Url
     * ) {
     *     Map<String, ILogEntryService> nodes = new HashMap<>();
     *     nodes.put("node1", new ProxyLogEntryService(node1Url));
     *     nodes.put("node2", new ProxyLogEntryService(node2Url));
     *     nodes.put("node3", new ProxyLogEntryService(node3Url));
     *     return nodes;
     * }
     */
}
