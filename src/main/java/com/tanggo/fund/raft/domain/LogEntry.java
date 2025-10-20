package com.tanggo.fund.raft.domain;

import lombok.Data;

// 命令行流水 binlog
@Data
public class LogEntry {
    private final String nodeId;
    private final int term;        // 创建时的任期号
    private final int index;       // 日志索引位置
    private final String command;  // 操作指令
    private boolean committed; // 是否已提交

    public LogEntry(String nodeId, int term, int index, String command) {
        this.nodeId = nodeId;
        this.term = term;
        this.index = index;
        this.command = command;
        this.committed = false;
    }

    // getter和setter方法
    public int getTerm() {
        return term;
    }

    public int getIndex() {
        return index;
    }

    public String getCommand() {
        return command;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }
}
