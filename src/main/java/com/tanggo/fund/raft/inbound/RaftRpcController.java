package com.tanggo.fund.raft.inbound;

import com.tanggo.fund.raft.service.ILogEntryService;
import com.tanggo.fund.raft.service.command.AppendEntriesCommand;
import com.tanggo.fund.raft.service.command.AppendEntriesResponse;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Raft JSON-RPC 2.0 服务端控制器
 * 处理来自其他 Raft 节点的远程调用
 */
@RestController
@RequestMapping("/raft/rpc")
public class RaftRpcController {

    private final ILogEntryService logEntryService;

    public RaftRpcController(ILogEntryService logEntryService) {
        this.logEntryService = logEntryService;
    }

    /**
     * 处理 JSON-RPC 2.0 请求
     */
    @PostMapping
    public Map<String, Object> handleJsonRpc(@RequestBody Map<String, Object> request) {
        String method = (String) request.get("method");
        List<Object> params = (List<Object>) request.get("params");
        Object id = request.get("id");

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        try {
            Object result = dispatchMethod(method, params);
            response.put("result", result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", -32603);
            error.put("message", "Internal error: " + e.getMessage());
            response.put("error", error);
        }

        return response;
    }

    /**
     * 分发 RPC 方法调用
     */
    @SuppressWarnings("unchecked")
    private Object dispatchMethod(String method, List<Object> params) {
        return switch (method) {
            case "raft_handleClientCommand" -> {
                String command = (String) params.get(0);
                yield logEntryService.handleClientCommand(command);
            }
            case "raft_handleAppendEntries" -> {
                // 从参数中构造 AppendEntriesCommand
                Map<String, Object> requestMap = (Map<String, Object>) params.get(0);
                AppendEntriesCommand cmd = parseAppendEntriesCommand(requestMap);
                AppendEntriesResponse resp = logEntryService.handleAppendEntries(cmd);
                yield convertAppendEntriesResponse(resp);
            }
            default -> throw new IllegalArgumentException("Unknown method: " + method);
        };
    }

    /**
     * 解析 AppendEntriesCommand
     */
    @SuppressWarnings("unchecked")
    private AppendEntriesCommand parseAppendEntriesCommand(Map<String, Object> map) {
        int term = ((Number) map.get("term")).intValue();
        int prevLogIndex = ((Number) map.get("prevLogIndex")).intValue();
        int prevLogTerm = ((Number) map.get("prevLogTerm")).intValue();
        List<Object> entriesData = (List<Object>) map.get("entries");
        int leaderCommit = ((Number) map.get("leaderCommit")).intValue();

        // TODO: 解析 entries 列表，暂时传 null
        return new AppendEntriesCommand(term, prevLogIndex, prevLogTerm, null, leaderCommit);
    }

    /**
     * 转换 AppendEntriesResponse 为 Map
     */
    private Map<String, Object> convertAppendEntriesResponse(AppendEntriesResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put("term", response.getTerm());
        result.put("success", response.isSuccess());
        result.put("matchIndex", response.getMatchIndex());
        return result;
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        status.put("service", "raft-rpc");
        return status;
    }
}
