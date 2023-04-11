package com.tencent.bk.devops.p4sync.task

import com.perforce.p4java.client.IClient
import com.perforce.p4java.core.IChangelistSummary
import com.perforce.p4java.core.file.IFileSpec
import com.perforce.p4java.impl.mapbased.rpc.RpcPropertyDefs
import com.perforce.p4java.impl.mapbased.rpc.stream.helper.RpcSocketHelper
import com.perforce.p4java.option.client.ParallelSyncOptions
import com.tencent.bk.devops.p4sync.task.constants.EMPTY
import com.tencent.bk.devops.p4sync.task.constants.NONE
import com.tencent.bk.devops.p4sync.task.constants.P4_CHANGELIST_MAX_MOST_RECENT
import com.tencent.bk.devops.p4sync.task.constants.P4_CHARSET
import com.tencent.bk.devops.p4sync.task.constants.P4_CLIENT
import com.tencent.bk.devops.p4sync.task.constants.P4_CONFIG_FILE_NAME
import com.tencent.bk.devops.p4sync.task.constants.P4_PORT
import com.tencent.bk.devops.p4sync.task.constants.P4_USER
import com.tencent.bk.devops.p4sync.task.p4.MoreSyncOptions
import com.tencent.bk.devops.p4sync.task.p4.P4Client
import com.tencent.bk.devops.p4sync.task.p4.Workspace
import com.tencent.bk.devops.p4sync.task.pojo.P4Repository
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Properties

open class SyncTask(builder: Builder) {

    private var charset: String
    private var properties: Properties
    private var userName: String
    private var password: String? = null
    private var repository: P4Repository
    private var syncOptions: MoreSyncOptions
    private var parallelSyncOptions: ParallelSyncOptions
    private var workspace: Workspace
    private var fileSpecs: List<IFileSpec>
    private var continueOnError = false
    private var autoCleanup = false
    private var unshelveId: Int? = null
    private val listeners: List<SyncTaskListener>

    init {
        checkNotNull(builder.userName)
        checkNotNull(builder.repository)
        checkNotNull(builder.syncOptions)
        checkNotNull(builder.parallelSyncOptions)
        checkNotNull(builder.workspace)
        this.charset = builder.charset
        this.properties = builder.properties
        this.userName = builder.userName!!
        this.password = builder.password
        this.repository = builder.repository!!
        this.syncOptions = builder.syncOptions!!
        this.parallelSyncOptions = builder.parallelSyncOptions!!
        this.workspace = builder.workspace!!
        this.fileSpecs = builder.fileSpecs
        this.continueOnError = builder.continueOnError
        this.listeners = builder.listeners
    }

    fun execute(): ExecuteResult {
        val p4Client = P4Client(
            uri = repository.getDepotUri(),
            userName = userName,
            password = password,
            charset,
            properties,
        )
        p4Client.use { p4Client ->
            val clientName = workspace.name
            val client = p4Client.getClient(clientName)
            val newClient = if (client != null) {
                // update
                p4Client.updateClient(workspace)
            } else {
                p4Client.createClient(workspace)
            }
            if (autoCleanup) {
                p4Client.cleanup(newClient)
            }
            listeners.forEach { it.before() }
            val executeResult = buildExecuteResult(p4Client, fileSpecs)
            sync(p4Client, newClient)
            listeners.forEach { it.after(executeResult) }
            unshelveId?.let {
                logger.info("unshelve id $unshelveId.")
                p4Client.unshelve(it, newClient)
            }
            saveConfig(newClient, repository.p4port, charset)
            return executeResult
        }
    }

    private fun saveConfig(client: IClient, uri: String, charsetName: String) {
        val p4user = client.server.userName
        val configFilePath = Paths.get(client.root, P4_CONFIG_FILE_NAME)
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

    private fun buildExecuteResult(p4Client: P4Client, fileSpecs: List<IFileSpec>): ExecuteResult {
        val result = ExecuteResult()
        result.depotUrl = repository.getDepotUri()
        result.stream = workspace.stream ?: EMPTY
        result.charset = charset
        result.workspacePath = workspace.root
        result.clientName = workspace.name
        val changeList = if (workspace.stream != null) {
            p4Client.getChangeListByStream(
                max = P4_CHANGELIST_MAX_MOST_RECENT,
                streamName = workspace.stream!!,
                fileSpecs = fileSpecs,
            )
        } else {
            p4Client.getChangeList(
                max = P4_CHANGELIST_MAX_MOST_RECENT,
                fileSpecs = fileSpecs,
            )
        }
        // 最新修改
        changeList.firstOrNull()?.let {
            val logChange = formatChange(it)
            logger.info(logChange)
            result.headCommitId = it.id.toString()
            result.headCommitComment = it.description
            result.headCommitClientId = it.clientId
            result.headCommitUser = it.username
        }
        result.changeList = changeList
        return result
    }

    private fun formatChange(change: IChangelistSummary): String {
        val format = SimpleDateFormat("yyyy/MM/dd")
        val date = change.date
        val desc = change.description.dropLast(1)
        return "Change ${change.id} on ${format.format(date)} by ${change.username}@${change.clientId} '$desc '"
    }

    open fun sync(p4Client: P4Client, client: IClient) {
        p4Client.sync(client, syncOptions, parallelSyncOptions, fileSpecs, continueOnError)
    }

    class Builder {
        var charset = NONE
        var properties = Properties()
        var userName: String? = null
        var password: String? = null
        var repository: P4Repository? = null
        var syncOptions: MoreSyncOptions? = null
        var parallelSyncOptions: ParallelSyncOptions? = null
        var workspace: Workspace? = null
        var fileSpecs = mutableListOf<IFileSpec>()
        var continueOnError = false
        var listeners = mutableListOf<SyncTaskListener>()
        var deleteClientAfterTask = false

        fun charset(charset: String): Builder {
            this.charset = charset
            return this
        }

        fun useProxy(proxyHost: String, proxyPort: Int): Builder {
            RpcSocketHelper.httpProxyHost = proxyHost
            RpcSocketHelper.httpProxyPort = proxyPort
            return this
        }

        fun userCredential(userName: String, password: String?): Builder {
            this.userName = userName
            this.password = password
            return this
        }

        fun withRepository(repository: P4Repository): Builder {
            this.repository = repository
            return this
        }

        fun writeTimeout(timeout: Int): Builder {
            properties.setProperty(RpcPropertyDefs.RPC_SOCKET_SO_TIMEOUT_NICK, timeout.toString())
            return this
        }

        fun syncOptions(syncOptions: MoreSyncOptions): Builder {
            this.syncOptions = syncOptions
            return this
        }

        fun parallelSyncOptions(parallelSyncOptions: ParallelSyncOptions): Builder {
            this.parallelSyncOptions = parallelSyncOptions
            return this
        }

        fun workspace(workspace: Workspace): Builder {
            this.workspace = workspace
            return this
        }

        fun fileSpecs(fileSpecs: List<IFileSpec>): Builder {
            this.fileSpecs.addAll(fileSpecs)
            return this
        }

        fun continueOnError(continueOnError: Boolean): Builder {
            this.continueOnError = continueOnError
            return this
        }

        fun addListener(listener: SyncTaskListener): Builder {
            listeners.add(listener)
            return this
        }

        fun deleteClientAfterTask(on: Boolean): Builder {
            this.deleteClientAfterTask = on
            return this
        }

        fun build(): SyncTask {
            return if (supportP4Cmd()) P4CmdSyncTask(this) else SyncTask(this)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SyncTask::class.java)
        private fun supportP4Cmd(): Boolean {
            return false
        }
    }
}
