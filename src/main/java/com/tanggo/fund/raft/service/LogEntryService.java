package com.tanggo.fund.raft.service;


import com.tanggo.fund.raft.domain.LogEntry;
import com.tanggo.fund.raft.domain.PeerRaftNode;
import com.tanggo.fund.raft.domain.RaftNode;
import com.tanggo.fund.raft.outbound.LogEntryRepo;
import com.tanggo.fund.raft.outbound.PeerRaftNodeRepo;
import com.tanggo.fund.raft.service.command.AppendEntriesCommand;
import com.tanggo.fund.raft.service.command.AppendEntriesResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

// 节点状态枚举

public class LogEntryService {

    private final RaftNode currentNode; //当前节点信息
    private final ScheduledExecutorService scheduler;
    private PeerRaftNodeRepo peerRaftNodeRepo;
    private LogEntryRepo logEntryRepo;
    private ScheduledFuture<?> heartbeatTask;

    public LogEntryService(String node1, Map<String, LogEntryService> nodes) {

        currentNode = new RaftNode("ss", null);
        this.scheduler = Executors.newScheduledThreadPool(3);

        // 启动定时任务
        startElectionTimeout();

    }


    // 处理客户端请求（仅领导者）
    public synchronized boolean handleClientCommand(String command) {
        if (!currentNode.isLeader()) {
            System.out.println("重定向到领导者: 本节点不是领导者");
            return false;
        }

        // 创建新日志条目
        int newIndex = log.size();
        LogEntry newEntry = new LogEntry(currentTerm, newIndex, command);

        logEntryRepo.insert(newEntry);
//        System.out.println("领导者节点 " + nodeId + " 追加日志: " + command + " (索引: " + newIndex + ")");

        // 开始复制到从节点
        replicateLog();
        return true;
    }

    // 日志复制到从节点
    private void replicateLog() {
        Map<String, PeerRaftNode> clusterNodes = peerRaftNodeRepo.query();
        for (String followerId : clusterNodes.keySet()) {
            if (!followerId.equals(nodeId)) {
                // 异步发送日志条目
                CompletableFuture.runAsync(() -> sendAppendEntries(followerId));
            }
        }
    }

    // 发送日志追加请求
    private void sendAppendEntries(String followerId) {
        int nextIdx = nextIndex.getOrDefault(followerId, 0);

        // 检查是否有新日志需要发送
        if (nextIdx <= log.size() - 1) {
            List<LogEntry> entries = new ArrayList<>();
            if (nextIdx < log.size()) {
                entries = log.subList(nextIdx, log.size());
            }

            int prevLogIndex = nextIdx - 1;
            int prevLogTerm = (prevLogIndex >= 0 && prevLogIndex < log.size()) ? log.get(prevLogIndex).getTerm() : 0;

            AppendEntriesCommand request = new AppendEntriesCommand(currentTerm, prevLogIndex, prevLogTerm, entries, commitIndex);

            try {
                // 模拟网络发送（实际实现需使用HTTP/gRPC）
//                RaftNode follower = clusterNodes.get(followerId);

                LogEntryService follower = null;
                AppendEntriesResponse response = follower.handleAppendEntries(request);

                // 处理响应
                processAppendResponse(followerId, response, nextIdx, entries.size());
            } catch (Exception e) {
                System.err.println("向节点 " + followerId + " 发送日志失败: " + e.getMessage());
            }
        } else {
            // 发送心跳
            sendHeartbeat(followerId);
        }
    }

    // 处理追加日志请求（跟随者侧）
    public synchronized AppendEntriesResponse handleAppendEntries(AppendEntriesCommand request) {
        // 重置选举超时（收到领导者消息）
        resetElectionTimeout();

        // 1. 任期检查
        if (request.getTerm() < currentTerm) {
            return new AppendEntriesResponse(currentTerm, false, log.size() - 1);
        }

        // 更新任期（如果请求任期更大）
        if (request.getTerm() > currentTerm) {
            currentTerm = request.getTerm();
            becomeFollower();
        }

        // 2. 日志一致性检查
        if (request.getPrevLogIndex() >= 0) {
            if (log.size() <= request.getPrevLogIndex() || (request.getPrevLogIndex() >= 0 && log.get(request.getPrevLogIndex()).getTerm() != request.getPrevLogTerm())) {
                return new AppendEntriesResponse(currentTerm, false, log.size() - 1);
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
        if (request.getLeaderCommit() > commitIndex) {
            commitIndex = Math.min(request.getLeaderCommit(), log.size() - 1);
            applyCommittedEntries();
        }

        return new AppendEntriesResponse(currentTerm, true, log.size() - 1);
    }

    // 应用已提交的日志到状态机
    private void applyCommittedEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = log.get(lastApplied);
            entry.setCommitted(true);

            // 实际应用中这里应该执行命令并更新状态机
            System.out.println("节点 " + nodeId + " 应用日志[" + lastApplied + "]: " + entry.getCommand());
        }
    }

    // 处理追加日志响应（领导者侧）
    private synchronized void processAppendResponse(String followerId, AppendEntriesResponse response, int sentIndex, int entryCount) {
        if (response.getTerm() > currentTerm) {
            // 发现更高任期，转为跟随者
            currentTerm = response.getTerm();
            becomeFollower();
            return;
        }

        if (response.isSuccess()) {
            // 更新对应从节点的匹配索引
            int newMatchIndex = sentIndex + entryCount - 1;
            matchIndex.put(followerId, newMatchIndex);
            nextIndex.put(followerId, newMatchIndex + 1);

            // 检查是否可以提交日志
            updateCommitIndex();
        } else {
            // 复制失败，回退nextIndex重试
            nextIndex.put(followerId, Math.max(sentIndex - 1, 0));
        }
    }

    // 更新提交索引（领导者）
    private void updateCommitIndex() {
        // 收集所有匹配索引
        List<Integer> matchIndexes = new ArrayList<>(matchIndex.values());
        matchIndexes.add(log.size() - 1); // 包括领导者自己

        // 排序并找到中位数（多数节点已复制的索引）
        Collections.sort(matchIndexes);
        int newCommitIndex = matchIndexes.get(matchIndexes.size() / 2);

        // 只能提交当前任期的日志
        if (newCommitIndex > commitIndex && newCommitIndex < log.size() && log.get(newCommitIndex).getTerm() == currentTerm) {
            commitIndex = newCommitIndex;
            applyCommittedEntries();

            // 通知从节点提交日志
            for (String followerId : clusterNodes.keySet()) {
                if (!followerId.equals(nodeId)) {
                    sendHeartbeat(followerId);
                }
            }
        }
    }

    // 发送心跳包
    private void sendHeartbeat(String followerId) {
        AppendEntriesMessage heartbeat = new AppendEntriesMessage(currentTerm, log.size() - 1, log.isEmpty() ? 0 : log.get(log.size() - 1).getTerm(), null, commitIndex);

        CompletableFuture.runAsync(() -> {
            try {
                RaftNode follower = clusterNodes.get(followerId);
                follower.handleAppendEntries(heartbeat);
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
            if (state == State.LEADER) {
                for (String followerId : clusterNodes.keySet()) {
                    if (!followerId.equals(nodeId)) {
                        sendHeartbeat(followerId);
                    }
                }
            }
        }, 0, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    // 状态转换方法
    private void becomeFollower() {
        state = State.FOLLOWER;
        votedFor = null;
        startElectionTimeout();
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }
    }

    private void becomeLeader() {
        currentNode.becomeLeader();

        // 初始化领导者状态
        for (String nodeId : clusterNodes.keySet()) {
            nextIndex.put(nodeId, log.size());
            matchIndex.put(nodeId, -1);
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
