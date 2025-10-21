package com.tanggo.fund.raft.service.command.impl;

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import com.googlecode.jsonrpc4j.ProxyUtil;
import com.tanggo.fund.raft.outbound.RaftRpcClient;
import com.tanggo.fund.raft.service.ILogEntryService;
import com.tanggo.fund.raft.service.command.AppendEntriesCommand;
import com.tanggo.fund.raft.service.command.AppendEntriesResponse;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Raft 服务代理实现
 * 通过 JSON-RPC 远程调用其他 Raft 节点的服务
 */
public class ProxyLogEntryService implements ILogEntryService {

    private final String rpcUrl;
    private final RaftRpcClient rpcClient;

    /**
     * 构造函数
     *
     * @param rpcUrl Raft 节点的 JSON-RPC 端点 URL (例如: http://localhost:8080/raft/rpc)
     */
    public ProxyLogEntryService(String rpcUrl) {
        this.rpcUrl = rpcUrl;
        this.rpcClient = createRpcClient(rpcUrl);
    }

    /**
     * 创建 RPC 客户端
     */
    private RaftRpcClient createRpcClient(String rpcUrl) {
        try {
            URL url = new URL(rpcUrl);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            JsonRpcHttpClient httpClient = new JsonRpcHttpClient(url, headers);

            return ProxyUtil.createClientProxy(
                    getClass().getClassLoader(),
                    RaftRpcClient.class,
                    httpClient
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Raft RPC client for URL: " + rpcUrl, e);
        }
    }

    /**
     * 处理客户端命令 - 通过 JSON-RPC 远程调用
     * @param command 命令字符串
     * @return 是否成功处理
     */
    @Override
    public boolean handleClientCommand(String command) {
        try {
            return rpcClient.handleClientCommand(command);
        } catch (Exception e) {
            System.err.println("远程调用 handleClientCommand 失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 处理追加日志请求 - 通过 JSON-RPC 远程调用
     * @param request 追加日志请求
     * @return 追加日志响应
     */
    @Override
    public AppendEntriesResponse handleAppendEntries(AppendEntriesCommand request) {
        try {
            return rpcClient.handleAppendEntries(request);
        } catch (Exception e) {
            System.err.println("远程调用 handleAppendEntries 失败: " + e.getMessage());
            // 返回失败响应
            return new AppendEntriesResponse(0, false, -1);
        }
    }

    /**
     * 打印日志 - 通过 JSON-RPC 远程调用
     */
    @Override
    public void printLog() {
        try {
            rpcClient.printLog();
        } catch (Exception e) {
            System.err.println("远程调用 printLog 失败: " + e.getMessage());
        }
    }

    /**
     * 获取 RPC URL
     */
    public String getRpcUrl() {
        return rpcUrl;
    }
}
