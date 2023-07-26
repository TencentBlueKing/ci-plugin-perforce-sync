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
import com.tencent.bk.devops.p4sync.task.pojo.RepositoryConfig
import com.tencent.bk.devops.p4sync.task.service.AuthService
import org.slf4j.LoggerFactory

@AtomService(paramClass = P4SyncParam::class)
class P4Sync : TaskAtom<P4SyncParam> {
    private val authService = AuthService()
    private val devopsApi = DevopsApi()
    override fun execute(context: AtomContext<P4SyncParam>) {
        with(context) {
            // 参数检查
            checkParam(param, result)
            if (result.status != Status.success) {
                return
            }
            // 获取仓库信息
            val repository = getRepositoryInfo(param)
            // 设置同步任务
            val syncTask = createBuilder(param)
                .withRepository(repository)
                .userCredential(repository.username, repository.password)
                .addListener(PipelineListener(param))
                .addListener(SetOutputListener(context))
                .build()
            // 开始同步
            syncTask.execute()
        }
    }

    fun createBuilder(p4SyncParam: P4SyncParam): SyncTask.Builder {
        with(p4SyncParam) {
            // 获取仓库信息
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
                .workspace(p4SyncParam.getWorkspace())
                .continueOnError(p4SyncParam.keepGoingOnError)
                .fileSpecs(fileSpecs)
                .syncOptions(syncOptions)
                .parallelSyncOptions(parallelSyncOptions)
                .charset(charsetName)
                .deleteClientAfterTask(clientName == null)
                .autoCleanup(autoCleanup)
            if (unshelveId != null) {
                builder.unshelveId(unshelveId)
            }
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
        }
    }

    private fun getRepositoryInfo(param: P4SyncParam): P4Repository {
        with(param) {
            val type = repositoryType ?: RepositoryType.URL.name
            return when (type) {
                RepositoryType.URL.name->{
                    require(p4port!=null)
                    require(ticketId!=null)
                    val credentialInfo = authService.getCredentialInfo(ticketId)
                    P4Repository(p4port,credentialInfo[0],credentialInfo[1])
                }
                RepositoryType.NAME.name,
                RepositoryType.ID.name->{
                    // 使用别名或ID匹配时需读取代码库信息
                    // 构建代码库配置
                    val repositoryConfig = RepositoryConfig(
                        repositoryHashId = repositoryHashId,
                        repositoryName = repositoryName,
                        repositoryType = RepositoryType.valueOf(type),
                    )
                    // 获取代码信息
                    val result = devopsApi.getRepository(repositoryConfig)
                    if (result.isNotOk() || result.data == null) {
                        logger.error("Fail to get the repositoryInfo($repositoryConfig) because of ${result.message}")
                        throw IllegalArgumentException(result.message!!)
                    }
                    logger.info("get the repo:${result.data}")
                    val ticketId = result.data!!.credentialId
                    val p4port = result.data!!.url
                    param.repositoryName = result.data!!.aliasName
                    param.repositoryHashId = result.data!!.repoHashId
                    val credentialInfo = authService.getCredentialInfo(ticketId)
                    P4Repository(p4port,credentialInfo[0],credentialInfo[1])
                }
                else -> error("Not support repository type $repositoryType.")
            }

        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(P4Sync::class.java)
    }
}
