package com.tencent.bk.devops.p4sync.task

import com.perforce.p4java.core.IChangelistSummary
import com.tencent.bk.devops.p4sync.task.api.DevopsApi
import com.tencent.bk.devops.p4sync.task.enum.RepositoryType
import com.tencent.bk.devops.p4sync.task.enum.ScmType
import com.tencent.bk.devops.p4sync.task.pojo.CommitData
import com.tencent.bk.devops.p4sync.task.pojo.P4SyncParam
import com.tencent.bk.devops.p4sync.task.pojo.PipelineBuildMaterial
import com.tencent.bk.devops.p4sync.task.pojo.RepositoryConfig
import org.slf4j.LoggerFactory

class PipelineListener(val param: P4SyncParam) : SyncTaskListener {

    override fun after(executeResult: ExecuteResult) {
        // 指定同步文件版本后需对修改记录进行排序/去重，新纪录前面
        var changeList = executeResult.changeList.distinctBy { it.id }.sortedBy { -it.id }
        // 对比历史构建，提取本次构建拉取的commit
        changeList = getDiffChangeLists(changeList, param, executeResult)
        if (changeList.isNotEmpty()) {
            // 保存原材料
            saveBuildMaterial(changeList, param)
            // 保存提交信息
            saveChangeCommit(changeList, param)
        } else {
            logger.info("Already up to date,Do not save commit")
        }
    }

    private fun getDiffChangeLists(
        sourceChangeList: List<IChangelistSummary>,
        param: P4SyncParam,
        executeResult: ExecuteResult,
    ): List<IChangelistSummary> {
        if (sourceChangeList.isEmpty()) {
            return sourceChangeList
        }
        val result = mutableListOf<IChangelistSummary>()
        with(param) {
            var repositoryConfig: RepositoryConfig? = null
            if (repositoryType != RepositoryType.URL.name) {
                repositoryConfig = RepositoryConfig(
                    repositoryHashId = repositoryHashId,
                    repositoryName = repositoryName,
                    repositoryType = RepositoryType.valueOf(repositoryType!!),
                )
            }
            // 获取历史信息
            val latestCommit = DevopsApi().getLatestCommit(
                pipelineId = pipelineId,
                elementId = pipelineTaskId,
                repoId = repositoryConfig?.getURLEncodeRepositoryId() ?: "",
                repositoryType = repositoryConfig?.repositoryType?.name ?: "",
            )
            if (latestCommit.data.isNullOrEmpty()) {
                return sourceChangeList
            }
            val first = latestCommit.data?.first() ?: return sourceChangeList
            // 上次构建最后的changeId
            executeResult.lastCommitId = first.commit
            sourceChangeList.forEach {
                if (it.id > first.commit.toInt()) {
                    result.add(it)
                }
            }
            return result
        }
    }

    private fun saveBuildMaterial(changeList: List<IChangelistSummary>, param: P4SyncParam) {
        changeList.first().let {
            if (param.repositoryName.isNullOrBlank()) {
                param.repositoryName = param.p4port
            }
            DevopsApi().saveBuildMaterial(
                mutableListOf(
                    PipelineBuildMaterial(
                        aliasName = param.repositoryName,
                        url = param.p4port!!,
                        branchName = param.stream,
                        newCommitId = "${it.id}",
                        newCommitComment = it.description,
                        commitTimes = changeList.size,
                        scmType = ScmType.CODE_P4.name,
                    ),
                ),
            )
        }
    }

    private fun saveChangeCommit(changeList: List<IChangelistSummary>, param: P4SyncParam) {
        with(param) {
            val commitData = changeList.map {
                CommitData(
                    type = ScmType.parse(ScmType.CODE_P4),
                    pipelineId = pipelineId,
                    buildId = pipelineBuildId,
                    commit = "${it.id}",
                    committer = it.username,
                    author = it.username,
                    commitTime = it.date.time / 1000, // 单位:秒
                    comment = it.description.trim(),
                    repoId = repositoryHashId,
                    repoName = repositoryName,
                    elementId = pipelineTaskId,
                    url = p4port,
                )
            }
            DevopsApi().addCommit(commitData)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineListener::class.java)
    }
}
