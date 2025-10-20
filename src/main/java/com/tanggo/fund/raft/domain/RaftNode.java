package com.tanggo.fund.raft.domain;

import lombok.Data;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class RaftNode {
    // 配置参数
    public static final int HEARTBEAT_INTERVAL = 150; // 心跳间隔(ms)
    public static final int ELECTION_TIMEOUT_MIN = 300; // 最小选举超时
    public static final int ELECTION_TIMEOUT_MAX = 600; // 最大选举超时
    // 节点核心属性状态
    private final String nodeId;
    // 领导者专用状态
    private final Map<String, Integer> nextIndex;   // 每个从节点的下一个日志索引
    private final Map<String, Integer> matchIndex;  // 每个从节点已复制的最高日志索引
    // 网络通信和线程池
    // 其它
    private final Random random = new Random();
    private String votedFor;
    private int commitIndex;
    private int lastApplied;
    private State state;
    private int currentTerm;

    public RaftNode(String nodeId, Map<String, RaftNode> clusterNodes) {
        this.nodeId = nodeId;
        this.state = State.FOLLOWER;
        this.currentTerm = 0;
        this.votedFor = null;
        this.commitIndex = -1;
        this.lastApplied = -1;
        this.nextIndex = new ConcurrentHashMap<>();
        this.matchIndex = new ConcurrentHashMap<>();
//        this.clusterNodes = new ConcurrentHashMap<>(clusterNodes);
//        this.scheduler = Executors.newScheduledThreadPool(3);


    }

    public boolean isLeader() {

        return state == State.LEADER;
    }

    public void printLog() {
        System.out.println("=== 节点 " + nodeId + " 日志状态 ===");
        System.out.println("角色: " + state + ", 任期: " + currentTerm + ", 提交索引: " + commitIndex + ", 最后应用: " + lastApplied);

    }

    public void becomeLeader() {
        state = State.LEADER;

        System.out.println("节点 " + nodeId + " 成为领导者，任期: " + currentTerm);

    }


    // 节点状态枚举
    public enum State {FOLLOWER, CANDIDATE, LEADER}


}
