package com.tencent.bk.devops.p4sync.task

import com.perforce.p4java.client.IClient
import com.perforce.p4java.core.IChangelistSummary
import com.perforce.p4java.core.file.FileSpecBuilder
import com.perforce.p4java.impl.mapbased.rpc.RpcPropertyDefs.RPC_SOCKET_SO_TIMEOUT_NICK
import com.perforce.p4java.impl.mapbased.rpc.stream.helper.RpcSocketHelper
import com.perforce.p4java.option.client.ParallelSyncOptions
import com.perforce.p4java.server.PerforceCharsets
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.AtomResult
import com.tencent.bk.devops.atom.pojo.StringData
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.p4sync.task.api.DevopsApi
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
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_P4_REPO_ID
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_P4_REPO_NAME
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_P4_REPO_PATH
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_TASKID
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_TICKET_ID
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_TYPE
import com.tencent.bk.devops.p4sync.task.constants.BLANK
import com.tencent.bk.devops.p4sync.task.constants.EMPTY
import com.tencent.bk.devops.p4sync.task.constants.P4_CHANGES_FILE_NAME
import com.tencent.bk.devops.p4sync.task.constants.P4_CHARSET
import com.tencent.bk.devops.p4sync.task.constants.P4_CLIENT
import com.tencent.bk.devops.p4sync.task.constants.P4_CONFIG_FILE_NAME
import com.tencent.bk.devops.p4sync.task.constants.P4_PORT
import com.tencent.bk.devops.p4sync.task.constants.P4_USER
import com.tencent.bk.devops.p4sync.task.enum.RepositoryType
import com.tencent.bk.devops.p4sync.task.enum.ScmType
import com.tencent.bk.devops.p4sync.task.p4.MoreSyncOptions
import com.tencent.bk.devops.p4sync.task.p4.P4Client
import com.tencent.bk.devops.p4sync.task.pojo.CommitData
import com.tencent.bk.devops.p4sync.task.pojo.P4SyncParam
import com.tencent.bk.devops.p4sync.task.pojo.PipelineBuildMaterial
import com.tencent.bk.devops.p4sync.task.service.AuthService
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.FileReader
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Properties
import kotlin.random.Random

@AtomService(paramClass = P4SyncParam::class)
class P4Sync : TaskAtom<P4SyncParam> {

    private val logger = LoggerFactory.getLogger(P4Sync::class.java)

    override fun execute(context: AtomContext<P4SyncParam>) {
        val param = context.param
        val result = context.result
        checkParam(param, result)
        if (result.status != Status.success) {
            return
        }
        val credentialInfo = AuthService(param, result, DevopsApi()).getCredentialInfo()
        if (param.httpProxy != null && param.httpProxy.contains(':')) {
            val proxyParam = param.httpProxy.split(':')
            RpcSocketHelper.httpProxyHost = proxyParam[0]
            RpcSocketHelper.httpProxyPort = proxyParam[1].toInt()
        }
        val userName = credentialInfo[0]
        val credential = credentialInfo[1]
        val executeResult = syncWithTry(param, result, userName, credential)
        setOutPut(context, executeResult)
    }

    fun syncWithTry(param: P4SyncParam, result: AtomResult, userName: String, credential: String): ExecuteResult {
        var sync: ExecuteResult? = null
        try {
            sync = sync(param, userName, credential, result)
            if (!sync.result) {
                result.status = Status.failure
            }
        } catch (e: Exception) {
            result.status = Status.failure
            result.message = e.message
            logger.error("同步失败", e)
        }
        return sync ?: ExecuteResult()
    }

    private fun sync(param: P4SyncParam, userName: String, credential: String, atomResult: AtomResult): ExecuteResult {
        with(param) {
            val useSSL = param.p4port.startsWith("ssl:")
            val p4client = P4Client(
                uri = if (useSSL) "p4javassl://${param.p4port.substring(4)}" else "p4java://${param.p4port}",
                userName = userName,
                password = credential,
                charsetName,
                getProperties(this)
            )
            p4client.use {
                return execute(param, p4client, atomResult)
            }
        }
    }

    private fun execute(
        param: P4SyncParam,
        p4client: P4Client,
        atomResult: AtomResult
    ): ExecuteResult {
        with(param) {
            val result = ExecuteResult()
            val client = param.getClient(p4client)
            try {
                result.depotUrl = p4client.uri
                result.stream = stream ?: EMPTY
                result.charset = charsetName
                result.workspacePath = client.root
                result.clientName = client.name
                logPreChange(client, result)
                saveChanges(p4client, client, result, atomResult, param)
                // 保存client信息
                save(client, p4port, charsetName)
                if (autoCleanup) {
                    p4client.cleanup(client)
                }
                val syncOptions = MoreSyncOptions(
                    forceUpdate, noUpdate, clientBypass,
                    serverBypass, quiet, safetyCheck, max
                )
                val parallelSyncOptions = ParallelSyncOptions(
                    batch, batchSize, minimum,
                    minimumSize, numberOfThreads, null
                )
                val fileSpecs = FileSpecBuilder.makeFileSpecList(getFileSpecList())
                val ret = p4client.sync(client, syncOptions, parallelSyncOptions, fileSpecs, keepGoingOnError)
                result.result = ret
                // unshelve
                unshelveId?.let {
                    logger.info("unshelve id $unshelveId.")
                    p4client.unshelve(unshelveId, client)
                }
                return result
            } finally {
                clientName ?: let {
                    // 删除临时client
                    p4client.deleteClient(client.name)
                }
            }
        }
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

    private fun checkParam(param: P4SyncParam, result: AtomResult) {
        with(param) {
            // 检查输出路径
            try {
                rootPath?.let { checkPathWriteAbility(rootPath) }
            } catch (e: Exception) {
                result.status = Status.failure
                result.message = "同步的文件输出路径不可用: ${e.message}"
            }
            // 检查字符集
            if (!PerforceCharsets.isSupported(charsetName)) {
                result.status = Status.failure
                result.message = "Charset $charsetName not supported."
            }
            // 检查代码库参数
            checkRepositoryInfo(param, result)
        }
    }

    private fun checkPathWriteAbility(path: String) {
        Files.createDirectories(Paths.get(path))
        val tmpFile = Paths.get(path, "check_${System.currentTimeMillis()}")
        val output = Files.newOutputStream(tmpFile)
        output.use { out ->
            Random.nextBytes(1024).inputStream().use {
                it.copyTo(out)
            }
        }
        Files.delete(tmpFile)
    }

    private fun save(client: IClient, uri: String, charsetName: String) {
        val p4user = client.server.userName
        val configFilePath = getP4ConfigPath(client)
        val outputStream = Files.newOutputStream(configFilePath)
        val printWriter = PrintWriter(outputStream)
        printWriter.use {
            printWriter.println("$P4_USER=$p4user")
            printWriter.println("$P4_PORT=$uri")
            printWriter.println("$P4_CLIENT=${client.name}")
            if (client.server.supportsUnicode()) {
                printWriter.println("$P4_CHARSET=$charsetName")
            }
            logger.info("Save p4 config to [${configFilePath.toFile().canonicalPath}] success.")
        }
    }

    private fun saveChanges(
        p4Client: P4Client,
        client: IClient,
        result: ExecuteResult,
        atomResult: AtomResult,
        param: P4SyncParam
    ) {
        val changesFilePath = getChangesLogPath(client)
        val changesOutput = Files.newOutputStream(changesFilePath)
        val changeWriter = PrintWriter(changesOutput)
        val changeList: List<IChangelistSummary>
        val changeSummary = if (client.stream != null) {
            changeList = p4Client.getChangeListByStream(1, client.stream)
            changeList.first()
        } else {
            changeList = p4Client.getChangeList(1)
            if (changeList.isNotEmpty()) {
                changeList.first()
            } else null
        }
        // 最新修改
        changeSummary?.let {
            val logChange = formatChange(it)
            result.headCommitId = it.id.toString()
            result.headCommitComment = it.description
            result.headCommitClientId = it.clientId
            result.headCommitUser = it.username
            logger.info(logChange)
            changeWriter.use {
                changeWriter.println(logChange)
                logger.info("Record change log to [${changesFilePath.toFile().canonicalPath}] success.")
            }
        }
        // 保存原材料
        saveBuildMaterial(changeList, param)
        // 保存提交信息
        saveChangeCommit(changeList, param)

    }

    private fun formatChange(change: IChangelistSummary): String {
        val format = SimpleDateFormat("yyyy/MM/dd")
        val date = change.date
        val desc = change.description.dropLast(1)
        return "Change ${change.id} on ${format.format(date)} by ${change.username}@${change.clientId} '$desc '"
    }

    private fun getChangesLogPath(client: IClient): Path {
        return Paths.get(client.root, P4_CHANGES_FILE_NAME)
    }

    private fun getP4ConfigPath(client: IClient): Path {
        return Paths.get(client.root, P4_CONFIG_FILE_NAME)
    }

    private fun logPreChange(client: IClient, result: ExecuteResult) {
        val changesFilePath = getChangesLogPath(client)
        val file = changesFilePath.toFile()
        if (!file.exists()) {
            return
        }
        val reader = BufferedReader(FileReader(file))
        reader.use {
            it.lines().findFirst().ifPresent { log ->
                val change = log.split(BLANK)[1]
                result.lastCommitId = change
                logger.info("Pre sync change: $log.")
            }
        }
    }

    private fun getProperties(param: P4SyncParam): Properties {
        val properties = Properties()
        properties.setProperty(RPC_SOCKET_SO_TIMEOUT_NICK, param.netMaxWait.toString())
        return properties
    }

    private fun checkRepositoryInfo(param: P4SyncParam, result: AtomResult) {
        with(param) {
            when (RepositoryType.valueOf(repositoryType)) {
                RepositoryType.ID -> {
                    repositoryHashId ?: run {
                        result.status = Status.failure
                        result.message = "代码库ID不能为空"
                    }
                    result.data[BK_REPO_P4_REPO_ID] = StringData(repositoryHashId)
                }
                RepositoryType.NAME -> {
                    repositoryName ?: run {
                        result.status = Status.failure
                        result.message = "代码库名称不能为空"
                    }
                    result.data[BK_REPO_P4_REPO_NAME] = StringData(repositoryName)
                }
                RepositoryType.URL -> {
                    result.data[BK_REPO_P4_REPO_PATH] = StringData(p4port)
                    null
                }
            }
        }
    }

    private fun saveBuildMaterial(changeList: List<IChangelistSummary>, param: P4SyncParam) {
        changeList.first().let {
            DevopsApi().saveBuildMaterial(
                mutableListOf(
                    PipelineBuildMaterial(
                        aliasName = param.repositoryName,
                        url = param.p4port,
                        branchName = param.stream,
                        newCommitId = "${it.id}",
                        newCommitComment = it.description,
                        commitTimes = changeList.size,
                        scmType = ScmType.CODE_P4.name
                    )
                )
            )
        }
    }

    private fun saveChangeCommit(changeList: List<IChangelistSummary>, param: P4SyncParam) {
        val commitData = changeList.map {
            CommitData(
                type = ScmType.parse(ScmType.CODE_P4),
                pipelineId = param.pipelineId,
                buildId = param.pipelineBuildId,
                commit = "${it.id}",
                committer = it.username,
                author = it.username,
                commitTime = it.date.time / 1000, // 单位:秒
                comment = it.description,
                repoId = param.repositoryHashId,
                repoName = param.repositoryName,
                elementId = param.pipelineTaskId
            )
        }
        DevopsApi().addCommit(commitData)
    }
}
