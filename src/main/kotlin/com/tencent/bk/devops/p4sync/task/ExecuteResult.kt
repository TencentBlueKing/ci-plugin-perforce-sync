package com.tencent.bk.devops.p4sync.task

import com.tencent.bk.devops.p4sync.task.constants.EMPTY

class ExecuteResult {
    var headCommitId: String = EMPTY
    var headCommitComment: String = EMPTY
    var headCommitClientId: String = EMPTY
    var headCommitUser: String = EMPTY
    var lastCommitId: String = EMPTY
    var workspacePath: String = EMPTY
    var depotUrl: String = EMPTY
    var stream: String = EMPTY
    var charset: String = EMPTY
    var clientName: String = EMPTY
    var result: Boolean = false
    override fun toString(): String {
        return "ExecuteResult(headCommitId=$headCommitId, " +
            "headCommitComment=$headCommitComment," +
            " headCommitClientId=$headCommitClientId," +
            " headCommitUser=$headCommitUser, " +
            "lastCommitId=$lastCommitId," +
            " workspacePath=$workspacePath, " +
            "depotUrl=$depotUrl, " +
            "stream=$stream, " +
            "charset=$charset)" +
            "clientName=$clientName"
    }
}
