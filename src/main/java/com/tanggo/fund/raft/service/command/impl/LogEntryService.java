package com.tanggo.fund.raft.service.command.impl;


import com.tanggo.fund.raft.domain.LogEntry;
import com.tanggo.fund.raft.domain.PeerRaftNode;
import com.tanggo.fund.raft.domain.RaftNode;
import com.tanggo.fund.raft.outbound.LogEntryRepo;
import com.tanggo.fund.raft.outbound.PeerRaftNodeRepo;
import com.tanggo.fund.raft.service.ILogEntryService;
import com.tanggo.fund.raft.service.command.AppendEntriesCommand;
import com.tanggo.fund.raft.service.command.AppendEntriesResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

// 节点状态枚举

public class LogEntryService implements ILogEntryService {

    private final RaftNode currentNode; //当前节点信息
    private final ScheduledExecutorService scheduler;
    private final Map<String, ILogEntryService> nodes;
    private PeerRaftNodeRepo peerRaftNodeRepo;
    private LogEntryRepo logEntryRepo;
    private ScheduledFuture<?> heartbeatTask;

    public LogEntryService(String node1, Map<String, ILogEntryService> nodes) {

        currentNode = new RaftNode("ss", null);
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.nodes = nodes;
        // 启动定时任务
        startElectionTimeout();

    }


    // 处理客户端请求（仅领导者）
    @Override
    public synchronized boolean handleClientCommand(String command) {
        if (!currentNode.isLeader()) {
            System.out.println("重定向到领导者: 本节点不是领导者");
            return false;
        }

        // 创建新日志条目
        List<LogEntry> log = logEntryRepo.query();
        int newIndex = log.size();
        LogEntry newEntry = new LogEntry(currentNode.getNodeId(), currentNode.getCurrentTerm(), newIndex, command);

        logEntryRepo.insert(newEntry);
        System.out.println("领导者节点 " + currentNode.getNodeId() + " 追加日志: " + command + " (索引: " + newIndex + ")");

        // 开始复制到从节点
        replicateLog();
        return true;
    }

    // 日志复制到从节点
    private void replicateLog() {
        Map<String, PeerRaftNode> clusterNodes = peerRaftNodeRepo.query();
        for (String followerId : clusterNodes.keySet()) {
            if (!followerId.equals(currentNode.getNodeId())) {
                // 异步发送日志条目
                CompletableFuture.runAsync(() -> sendAppendEntries(followerId));
            }
        }
    }

    // 发送日志追加请求
    private void sendAppendEntries(String followerId) {
        List<LogEntry> log = logEntryRepo.query();
        int nextIdx = currentNode.getNextIndex().getOrDefault(followerId, 0);

        // 检查是否有新日志需要发送
        if (nextIdx <= log.size() - 1) {
            List<LogEntry> entries = new ArrayList<>();
            if (nextIdx < log.size()) {
                entries = log.subList(nextIdx, log.size());
            }

            int prevLogIndex = nextIdx - 1;
            int prevLogTerm = (prevLogIndex >= 0 && prevLogIndex < log.size()) ? log.get(prevLogIndex).getTerm() : 0;

            AppendEntriesCommand request = new AppendEntriesCommand(currentNode.getCurrentTerm(), prevLogIndex, prevLogTerm, entries, currentNode.getCommitIndex());

            try {
                // 模拟网络发送（实际实现需使用HTTP/gRPC）
                ILogEntryService follower = nodes.get(followerId);
                if (follower != null) {
                    AppendEntriesResponse response = follower.handleAppendEntries(request);
                    // 处理响应
                    processAppendResponse(followerId, response, nextIdx, entries.size());
                }
            } catch (Exception e) {
                System.err.println("向节点 " + followerId + " 发送日志失败: " + e.getMessage());
            }
        } else {
            // 发送心跳
            sendHeartbeat(followerId);
        }
    }

    // 处理追加日志请求（跟随者侧）
    @Override
    public synchronized AppendEntriesResponse handleAppendEntries(AppendEntriesCommand request) {
        // 重置选举超时（收到领导者消息）
        resetElectionTimeout();

        List<LogEntry> log = logEntryRepo.query();

        // 1. 任期检查
        if (request.getTerm() < currentNode.getCurrentTerm()) {
            return new AppendEntriesResponse(currentNode.getCurrentTerm(), false, log.size() - 1);
        }

        // 更新任期（如果请求任期更大）
        if (request.getTerm() > currentNode.getCurrentTerm()) {
            currentNode.setCurrentTerm(request.getTerm());
            becomeFollower();
        }

        // 2. 日志一致性检查
        if (request.getPrevLogIndex() >= 0) {
            if (log.size() <= request.getPrevLogIndex() || (request.getPrevLogIndex() >= 0 && log.get(request.getPrevLogIndex()).getTerm() != request.getPrevLogTerm())) {
                return new AppendEntriesResponse(currentNode.getCurrentTerm(), false, log.size() - 1);
            }
        }

        // 3. 追加新日志条目
        if (request.getEntries() != null && !request.getEntries().isEmpty()) {
            int index = request.getPrevLogIndex() + 1;

            for (LogEntry newEntry : request.getEntries()) {
                if (index < log.size()) {
                    // 冲突检测：如果现有日志条目与新的冲突，则删除后续所有
                    if (log.get(index).getTerm() != newEntry.getTerm()) {
                        log = new ArrayList<>(log.subList(0, index));
                        log.add(newEntry);
                    }
                } else {
                    // 追加新日志
                    log.add(newEntry);
                }
                index++;
            }
        }

        // 4. 更新提交索引
        if (request.getLeaderCommit() > currentNode.getCommitIndex()) {
            currentNode.setCommitIndex(Math.min(request.getLeaderCommit(), log.size() - 1));
            applyCommittedEntries();
        }

        return new AppendEntriesResponse(currentNode.getCurrentTerm(), true, log.size() - 1);
    }

    // 应用已提交的日志到状态机
    private void applyCommittedEntries() {
        List<LogEntry> log = logEntryRepo.query();
        while (currentNode.getLastApplied() < currentNode.getCommitIndex()) {
            currentNode.setLastApplied(currentNode.getLastApplied() + 1);
            LogEntry entry = log.get(currentNode.getLastApplied());
            entry.setCommitted(true);

            // 实际应用中这里应该执行命令并更新状态机
            System.out.println("节点 " + currentNode.getNodeId() + " 应用日志[" + currentNode.getLastApplied() + "]: " + entry.getCommand());
        }
    }

    // 处理追加日志响应（领导者侧）
    private synchronized void processAppendResponse(String followerId, AppendEntriesResponse response, int sentIndex, int entryCount) {
        if (response.getTerm() > currentNode.getCurrentTerm()) {
            // 发现更高任期，转为跟随者
            currentNode.setCurrentTerm(response.getTerm());
            becomeFollower();
            return;
        }

        if (response.isSuccess()) {
            // 更新对应从节点的匹配索引
            int newMatchIndex = sentIndex + entryCount - 1;
            currentNode.getMatchIndex().put(followerId, newMatchIndex);
            currentNode.getNextIndex().put(followerId, newMatchIndex + 1);

            // 检查是否可以提交日志
            updateCommitIndex();
        } else {
            // 复制失败，回退nextIndex重试
            currentNode.getNextIndex().put(followerId, Math.max(sentIndex - 1, 0));
        }
    }

    // 更新提交索引（领导者）
    private void updateCommitIndex() {
        List<LogEntry> log = logEntryRepo.query();
        // 收集所有匹配索引
        List<Integer> matchIndexes = new ArrayList<>(currentNode.getMatchIndex().values());
        matchIndexes.add(log.size() - 1); // 包括领导者自己

        // 排序并找到中位数（多数节点已复制的索引）
        Collections.sort(matchIndexes);
        int newCommitIndex = matchIndexes.get(matchIndexes.size() / 2);

        // 只能提交当前任期的日志
        if (newCommitIndex > currentNode.getCommitIndex() && newCommitIndex < log.size() && log.get(newCommitIndex).getTerm() == currentNode.getCurrentTerm()) {
            currentNode.setCommitIndex(newCommitIndex);
            applyCommittedEntries();

            // 通知从节点提交日志
            Map<String, PeerRaftNode> clusterNodes = peerRaftNodeRepo.query();
            for (String followerId : clusterNodes.keySet()) {
                if (!followerId.equals(currentNode.getNodeId())) {
                    sendHeartbeat(followerId);
                }
            }
        }
    }

    // 发送心跳包
    private void sendHeartbeat(String followerId) {
        List<LogEntry> log = logEntryRepo.query();
        AppendEntriesCommand heartbeat = new AppendEntriesCommand(currentNode.getCurrentTerm(), log.size() - 1, log.isEmpty() ? 0 : log.get(log.size() - 1).getTerm(), null, currentNode.getCommitIndex());

        CompletableFuture.runAsync(() -> {
            try {
                ILogEntryService follower = nodes.get(followerId);
                if (follower != null) {
                    follower.handleAppendEntries(heartbeat);
                }
            } catch (Exception e) {
                System.err.println("向节点 " + followerId + " 发送心跳失败: " + e.getMessage());
            }
        });
    }

    // 启动心跳定时任务（领导者）
    private void startHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }

        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (currentNode.getState() == RaftNode.State.LEADER) {
                Map<String, PeerRaftNode> clusterNodes = peerRaftNodeRepo.query();
                for (String followerId : clusterNodes.keySet()) {
                    if (!followerId.equals(currentNode.getNodeId())) {
                        sendHeartbeat(followerId);
                    }
                }
            }
        }, 0, RaftNode.HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    // 状态转换方法
    private void becomeFollower() {
        currentNode.setState(RaftNode.State.FOLLOWER);
        currentNode.setVotedFor(null);
        startElectionTimeout();
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }
    }

    private void becomeLeader() {
        currentNode.becomeLeader();

        List<LogEntry> log = logEntryRepo.query();
        // 初始化领导者状态
        Map<String, PeerRaftNode> clusterNodes = peerRaftNodeRepo.query();
        for (String nodeId : clusterNodes.keySet()) {
            currentNode.getNextIndex().put(nodeId, log.size());
            currentNode.getMatchIndex().put(nodeId, -1);
        }

        startHeartbeat();
    }

    // 选举超时处理
    private void startElectionTimeout() {
        scheduler.schedule(() -> {
            if (!currentNode.isLeader()) {
                startElection();
            }

        }, currentNode.getRandom().nextInt(RaftNode.ELECTION_TIMEOUT_MAX - RaftNode.ELECTION_TIMEOUT_MIN) + RaftNode.ELECTION_TIMEOUT_MIN, TimeUnit.MILLISECONDS);
    }

    private void resetElectionTimeout() {
        startElectionTimeout();
    }

    private void startElection() {
        //todo
        // 省略选举具体实现...
    }

    // 工具方法
    @Override
    public void printLog() {

        currentNode.printLog();

        List<LogEntry> log = logEntryRepo.query();
        for (int i = 0; i < log.size(); i++) {
            LogEntry entry = log.get(i);
            System.out.println("[" + i + "] 任期:" + entry.getTerm() + " 指令:" + entry.getCommand() + (entry.isCommitted() ? " [已提交]" : " [未提交]"));
        }
        System.out.println();
    }
}
