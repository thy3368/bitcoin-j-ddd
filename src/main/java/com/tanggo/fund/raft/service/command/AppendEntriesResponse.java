package com.tanggo.fund.raft.service.command;

public class AppendEntriesResponse {
    private final int term;        // 当前任期号
    private final boolean success; // 是否成功
    private final int matchIndex;  // 已匹配的日志索引

    public AppendEntriesResponse(int term, boolean success, int matchIndex) {
        this.term = term;
        this.success = success;
        this.matchIndex = matchIndex;
    }

    // getter方法
    public int getTerm() {
        return term;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getMatchIndex() {
        return matchIndex;
    }
}
