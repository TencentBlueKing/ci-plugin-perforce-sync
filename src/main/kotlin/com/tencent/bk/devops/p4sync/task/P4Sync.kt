package com.tencent.bk.devops.p4sync.task

import com.perforce.p4java.client.IClient
import com.perforce.p4java.core.file.FileSpecBuilder
import com.perforce.p4java.impl.mapbased.rpc.stream.helper.RpcSocketHelper
import com.perforce.p4java.option.client.ParallelSyncOptions
import com.perforce.p4java.server.PerforceCharsets
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.AtomResult
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.p4sync.task.constants.P4_CLIENT
import com.tencent.bk.devops.p4sync.task.constants.P4_CONFIG_FILE_NAME
import com.tencent.bk.devops.p4sync.task.constants.P4_PORT
import com.tencent.bk.devops.p4sync.task.constants.P4_USER
import com.tencent.bk.devops.p4sync.task.enum.ticket.CredentialType
import com.tencent.bk.devops.p4sync.task.p4.MoreSyncOptions
import com.tencent.bk.devops.p4sync.task.p4.P4Client
import com.tencent.bk.devops.p4sync.task.pojo.P4SyncParam
import com.tencent.bk.devops.p4sync.task.util.CredentialUtils
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.random.Random

@AtomService(paramClass = P4SyncParam::class)
class P4Sync : TaskAtom<P4SyncParam> {

    private val logger = LoggerFactory.getLogger(P4Sync::class.java)

    override fun execute(context: AtomContext<P4SyncParam>) {
        val param = context.param
        val result = context.result
        val ticketId = param.ticketId
        checkParam(param, result)
        if (result.status != Status.success) {
            return
        }
        val (data, type) = CredentialUtils.getCredentialWithType(ticketId)
        if (type != CredentialType.USERNAME_PASSWORD) {
            result.message = "凭证错误【$type】，需要用户名+密码类型的凭证"
            result.status = Status.failure
            return
        }
        if (param.httpProxy != null && param.httpProxy.contains(':')) {
            val proxyParam = param.httpProxy.split(':')
            RpcSocketHelper.httpProxyHost = proxyParam[0]
            RpcSocketHelper.httpProxyPort = proxyParam[1].toInt()
        }
        val userName = data[0]
        val credential = data[1]
        syncWithTry(param, result, userName, credential)
    }

    fun syncWithTry(param: P4SyncParam, result: AtomResult, userName: String, credential: String) {
        try {
            sync(param, userName, credential)
        } catch (e: Exception) {
            result.status = Status.failure
            result.message = e.message
            logger.error("同步失败", e)
        }
    }

    private fun sync(param: P4SyncParam, userName: String, credential: String) {
        with(param) {
            val useSSL = param.p4port.startsWith("ssl:")
            val p4client = P4Client(
                uri = if (useSSL) "p4javassl://${param.p4port.substring(4)}" else "p4java://${param.p4port}",
                userName = userName,
                password = credential,
                charsetName
            )
            p4client.use {
                val client = param.getClient(p4client)
                val syncOptions = MoreSyncOptions(
                    forceUpdate, noUpdate, clientBypass,
                    serverBypass, quiet, safetyCheck, max
                )
                val parallelSyncOptions = ParallelSyncOptions(
                    batch, batchSize, minimum,
                    minimumSize, numberOfThreads, null
                )
                val fileSpecs = FileSpecBuilder.makeFileSpecList(fileRevSpec)
                p4client.sync(client, syncOptions, parallelSyncOptions, fileSpecs)
                // unshelve
                unshelveId?.let {
                    logger.info("unshelve id $unshelveId.")
                    p4client.unshelve(unshelveId, client)
                }
                // 保存client信息
                save(client, p4port)
            }
        }
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
            if (!PerforceCharsets.isSupported(charsetName)) {
                result.status = Status.failure
                result.message = "Charset $charsetName not supported."
            }
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

    private fun save(client: IClient, uri: String) {
        val p4root = client.root
        val p4user = client.server.userName
        val configFilePath = Paths.get(p4root, P4_CONFIG_FILE_NAME)
        val outputStream = Files.newOutputStream(configFilePath)
        val printWriter = PrintWriter(outputStream)
        printWriter.use {
            printWriter.println("$P4_USER=$p4user")
            printWriter.println("$P4_PORT=$uri")
            printWriter.print("$P4_CLIENT=${client.name}")
            logger.info("保存工作空间配置文件${configFilePath.toFile().canonicalPath}成功")
        }
    }
}
