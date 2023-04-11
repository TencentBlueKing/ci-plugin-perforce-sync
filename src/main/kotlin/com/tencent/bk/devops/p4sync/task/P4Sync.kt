package com.tencent.bk.devops.p4sync.task

import com.perforce.p4java.core.file.FileSpecBuilder
import com.perforce.p4java.option.client.ParallelSyncOptions
import com.perforce.p4java.server.PerforceCharsets
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.AtomResult
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.p4sync.task.api.DevopsApi
import com.tencent.bk.devops.p4sync.task.enum.RepositoryType
import com.tencent.bk.devops.p4sync.task.p4.MoreSyncOptions
import com.tencent.bk.devops.p4sync.task.pojo.P4Repository
import com.tencent.bk.devops.p4sync.task.pojo.P4SyncParam
import com.tencent.bk.devops.p4sync.task.service.AuthService
import org.slf4j.LoggerFactory

@AtomService(paramClass = P4SyncParam::class)
class P4Sync : TaskAtom<P4SyncParam> {
    override fun execute(context: AtomContext<P4SyncParam>) {
        with(context) {
            // 参数检查
            checkParam(param, result)
            if (result.status != Status.success) {
                return
            }
            // 获取凭证信息
            val credentialInfo = AuthService(param, result, DevopsApi()).getCredentialInfo()
            // 设置同步任务
            val syncTask = createBuilder(param)
                .userCredential(credentialInfo[0], credentialInfo[1])
                .addListener(SetOutputListener(context))
                .addListener(PipelineListener(param))
                .build()
            // 开始同步
            syncTask.execute()
        }
    }

    private fun createBuilder(p4SyncParam: P4SyncParam): SyncTask.Builder {
        with(p4SyncParam) {
            // 获取仓库信息
            val repository = P4Repository(p4port)
            val fileSpecs = FileSpecBuilder.makeFileSpecList(getFileSpecList())
            val syncOptions = MoreSyncOptions(
                forceUpdate,
                noUpdate,
                clientBypass,
                serverBypass,
                quiet,
                safetyCheck,
                max,
            )
            val parallelSyncOptions = ParallelSyncOptions(
                batch,
                batchSize,
                minimum,
                minimumSize,
                numberOfThreads,
                null,
            )
            val builder = SyncTask.Builder()
                .withRepository(repository)
                .workspace(p4SyncParam.getWorkspace())
                .continueOnError(p4SyncParam.keepGoingOnError)
                .fileSpecs(fileSpecs)
                .syncOptions(syncOptions)
                .parallelSyncOptions(parallelSyncOptions)
                .charset(charsetName)
                .deleteClientAfterTask(clientName == null)
            if (httpProxy != null && httpProxy.contains(':')) {
                val proxyParam = httpProxy.split(':')
                builder.useProxy(proxyParam[0], proxyParam[1].toInt())
            }
            if (netMaxWait > 0) {
                builder.writeTimeout(netMaxWait)
            }
            return builder
        }
    }

    private fun checkParam(param: P4SyncParam, result: AtomResult) {
        with(param) {
            // 检查字符集
            if (!PerforceCharsets.isSupported(charsetName)) {
                result.status = Status.failure
                result.message = "Charset $charsetName not supported."
            }
            // 检查代码库参数
            checkRepositoryInfo(param, result)
        }
    }

    private fun checkRepositoryInfo(param: P4SyncParam, result: AtomResult) {
        with(param) {
            // 代码库类型若为空则进行默认值处理
            repositoryType = repositoryType ?: RepositoryType.URL.name
            if (repositoryType == RepositoryType.ID.name && repositoryHashId == null) {
                result.status = Status.failure
                result.message = "The repository hashId cannot be empty"
            }
            if (repositoryType == RepositoryType.NAME.name && repositoryName == null) {
                result.status = Status.failure
                result.message = "The repository name cannot be empty"
            }
            if (repositoryType == RepositoryType.URL.name && p4port.isEmpty()) {
                result.status = Status.failure
                result.message = "The repository p4port cannot be empty"
            }
            if (!RepositoryType.values().map { it.name }.contains(repositoryType)) {
                result.status = Status.failure
                result.message = "Not support repository type $repositoryType"
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(P4Sync::class.java)
    }
}
