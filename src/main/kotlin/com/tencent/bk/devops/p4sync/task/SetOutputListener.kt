package com.tencent.bk.devops.p4sync.task

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.pojo.StringData
import com.tencent.bk.devops.p4sync.task.constants.BK_CI_P4_DEPOT_HEAD_CHANGE_CLIENT_ID
import com.tencent.bk.devops.p4sync.task.constants.BK_CI_P4_DEPOT_HEAD_CHANGE_COMMENT
import com.tencent.bk.devops.p4sync.task.constants.BK_CI_P4_DEPOT_HEAD_CHANGE_ID
import com.tencent.bk.devops.p4sync.task.constants.BK_CI_P4_DEPOT_HEAD_CHANGE_USER
import com.tencent.bk.devops.p4sync.task.constants.BK_CI_P4_DEPOT_LAST_CHANGE_ID
import com.tencent.bk.devops.p4sync.task.constants.BK_CI_P4_DEPOT_P4_CHARSET
import com.tencent.bk.devops.p4sync.task.constants.BK_CI_P4_DEPOT_PORT
import com.tencent.bk.devops.p4sync.task.constants.BK_CI_P4_DEPOT_STREAM
import com.tencent.bk.devops.p4sync.task.constants.BK_CI_P4_DEPOT_WORKSPACE_PATH
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_CONTAINER_ID
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_DEPOT_P4_CHARSET
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_DEPOT_PORT
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_DEPOT_STREAM
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_LOCAL_PATH
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_P4_CLIENT_NAME
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_P4_REPO_NAME
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_P4_REPO_PATH
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_TASKID
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_TICKET_ID
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_TYPE
import com.tencent.bk.devops.p4sync.task.pojo.P4SyncParam

class SetOutputListener(val context: AtomContext<P4SyncParam>) : SyncTaskListener {

    override fun after(executeResult: ExecuteResult) {
        setOutPut(context, executeResult)
    }

    private fun setOutPut(context: AtomContext<P4SyncParam>, executeResult: ExecuteResult) {
        context.result.data[BK_CI_P4_DEPOT_HEAD_CHANGE_ID] = StringData(executeResult.headCommitId)
        context.result.data[BK_CI_P4_DEPOT_HEAD_CHANGE_COMMENT] = StringData(executeResult.headCommitComment)
        context.result.data[BK_CI_P4_DEPOT_HEAD_CHANGE_CLIENT_ID] = StringData(executeResult.headCommitClientId)
        context.result.data[BK_CI_P4_DEPOT_HEAD_CHANGE_USER] = StringData(executeResult.headCommitUser)
        context.result.data[BK_CI_P4_DEPOT_LAST_CHANGE_ID] = StringData(executeResult.lastCommitId)
        context.result.data[BK_CI_P4_DEPOT_WORKSPACE_PATH] = StringData(executeResult.workspacePath)
        context.result.data[BK_CI_P4_DEPOT_PORT] = StringData(executeResult.depotUrl)
        context.result.data[BK_CI_P4_DEPOT_STREAM] = StringData(executeResult.stream)
        context.result.data[BK_CI_P4_DEPOT_P4_CHARSET] = StringData(executeResult.charset)
        context.result.data[BK_REPO_P4_REPO_NAME] = StringData(executeResult.repositoryAliasName)
        context.result.data[BK_REPO_P4_REPO_PATH] = StringData(executeResult.depotUrl)
        // 设置CodeCC扫描需要的仓库信息
        setOutPutForCodeCC(context, executeResult)
    }

    private fun setOutPutForCodeCC(context: AtomContext<P4SyncParam>, executeResult: ExecuteResult) {
        val taskId = context.param.pipelineTaskId
        context.result.data[BK_REPO_TASKID + taskId] = StringData(context.param.pipelineTaskId)
        context.result.data[BK_REPO_CONTAINER_ID + taskId] =
            StringData(context.allParameters["pipeline.job.id"]?.toString() ?: "")
        context.result.data[BK_REPO_TYPE + taskId] = StringData("perforce")
        context.result.data[BK_REPO_TICKET_ID + taskId] = StringData(context.param.ticketId)
        context.result.data[BK_REPO_DEPOT_PORT + taskId] = StringData(executeResult.depotUrl)
        context.result.data[BK_REPO_DEPOT_STREAM + taskId] = StringData(executeResult.stream)
        context.result.data[BK_REPO_DEPOT_P4_CHARSET + taskId] = StringData(executeResult.charset)
        context.result.data[BK_REPO_P4_CLIENT_NAME + taskId] = StringData(executeResult.clientName)
        context.result.data[BK_REPO_LOCAL_PATH + taskId] = StringData(context.param.rootPath ?: "")
    }
}
