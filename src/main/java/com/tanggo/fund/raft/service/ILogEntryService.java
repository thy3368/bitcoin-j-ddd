package com.tanggo.fund.raft.service;

import com.tanggo.fund.raft.service.command.AppendEntriesCommand;
import com.tanggo.fund.raft.service.command.AppendEntriesResponse;

public interface ILogEntryService {
    // 处理客户端请求（仅领导者）
    boolean handleClientCommand(String command);

    // 处理追加日志请求（跟随者侧）
    AppendEntriesResponse handleAppendEntries(AppendEntriesCommand request);

    // 工具方法
    void printLog();
}
