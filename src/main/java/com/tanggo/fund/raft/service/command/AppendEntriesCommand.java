package com.tanggo.fund.raft.service.command;

import com.tanggo.fund.raft.domain.LogEntry;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AppendEntriesCommand {
    private final int term;            // 领导者的任期
    private final int prevLogIndex;    // 上一条日志的索引
    private final int prevLogTerm;     // 上一条日志的任期
    private final List<LogEntry> entries; // 要复制的日志条目
    private final int leaderCommit;    // 领导者的提交索引

    // 构造方法、getter和setter
    public AppendEntriesCommand(int term, int prevLogIndex, int prevLogTerm, List<LogEntry> entries, int leaderCommit) {
        this.term = term;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        this.leaderCommit = leaderCommit;
    }
}
