package com.tencent.bk.devops.p4sync.task

import com.perforce.p4java.client.IClient
import com.perforce.p4java.core.file.FileSpecBuilder
import com.perforce.p4java.core.file.IFileSpec
import com.perforce.p4java.option.client.ParallelSyncOptions
import com.perforce.p4java.option.client.SyncOptions
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.AtomResult
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.p4sync.task.p4.MoreSyncOptions
import com.tencent.bk.devops.p4sync.task.p4.P4Client
import com.tencent.bk.devops.p4sync.task.pojo.P4SyncParam
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
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
        syncWithTry(param, result)
    }

    fun syncWithTry(param: P4SyncParam, result: AtomResult) {
        try {
            sync(param)
        } catch (e: Exception) {
            result.status = Status.failure
            result.message = e.message
            logger.error("同步失败", e)
        }
    }

    private fun sync(param: P4SyncParam) {
        with(param) {
            val p4client = P4Client(
                uri = "p4java://${param.p4port}",
                userName = param.userName,
                password = param.password,
                ticket = ticket,
                serverId = serverId
            )
            val client = param.getClient(p4client)
            val syncOptions = MoreSyncOptions(
                forceUpdate, noUpdate, clientBypass,
                serverBypass, quiet, safetyCheck, max
            )
            val parallelSyncOptions = ParallelSyncOptions(
                batch, batchSize, minimum,
                minimumSize, numberOfThreads, null
            )
            if (fileRevSpec != null) {
                val fileSpecs = FileSpecBuilder.makeFileSpecList(fileRevSpec)
                logSync(p4client, client, fileSpecs, syncOptions, parallelSyncOptions)
            } else {
                // 同步所有文件
                logSync(p4client, client, null, syncOptions, parallelSyncOptions)
            }
            if (isTempClient()) {
                p4client.deleteClient(tempClientName, false)
            }
        }
    }

    private fun logSync(
        p4client: P4Client,
        client: IClient,
        fileSpecs: MutableList<IFileSpec>?,
        syncOptions: SyncOptions,
        parallelSyncOptions: ParallelSyncOptions
    ): List<IFileSpec> {
        val syncs = p4client.sync(
            client,
            syncOptions, parallelSyncOptions, fileSpecs?.toList()
        )
        syncs.run { logSyncResults(this) }
        return syncs
    }

    fun checkParam(param: P4SyncParam, result: AtomResult) {
        with(param) {
            // 用户凭证检查
            if (ticket == null && password == null) {
                result.status = Status.failure
                result.message = errorMsgUser
            }
            // client配置检查
            if (clientName == null && (depotSpec == null || outPath == null)) {
                result.status = Status.failure
                result.message = errorMsgClient
            }
            // 检查输出路径
            outPath?.let {
                try {
                    checkPathWriteAbility(outPath)
                } catch (e: Exception) {
                    result.status = Status.failure
                    result.message = "同步的文件输出路径不可用: ${e.message}"
                }
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

    private fun logSyncResults(fileSpecs: List<IFileSpec>) {
        if (fileSpecs.size == 1 && fileSpecs.first().clientPath == null) {
            // -q的情况下toString()可能为null
            val msg = fileSpecs.first().toString() ?: return
            if (msg.contains("up-to-date")) {
                logger.info(msg.replace("ERROR: ", ""))
                return
            }
        }
        fileSpecs.forEach {
            logger.info("同步文件[$it]到${it.clientPath}")
        }
    }

    companion object {
        const val errorMsgUser = "ticket和password必须填写一个"
        const val errorMsgClient = "客户端名称或者（仓库和同步文件存放路径）必须填写一个"
    }
}
