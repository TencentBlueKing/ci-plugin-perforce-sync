package com.tencent.bk.devops.p4sync.task

class ExecuteResult {
    var headCommitId: String? = null
    var headCommitComment: String? = null
    var headCommitClientId: String? = null
    var headCommitUser: String? = null
    var lastCommitId: String? = null
    var workspacePath: String? = null
    var depotUrl: String? = null
    var stream: String? = null
    var charset: String? = null
    override fun toString(): String {
        return "ExecuteResult(headCommitId=$headCommitId, " +
            "headCommitComment=$headCommitComment," +
            " headCommitClientId=$headCommitClientId," +
            " headCommitUser=$headCommitUser, " +
            "lastCommitId=$lastCommitId," +
            " workspacePath=$workspacePath, " +
            "depotUrl=$depotUrl, " +
            "stream=$stream, " +
            "charset=$charset)"
    }
}
