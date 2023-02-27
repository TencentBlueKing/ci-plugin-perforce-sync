package com.tencent.bk.devops.p4sync.task.p4

import com.perforce.p4java.client.IClient
import com.perforce.p4java.client.IClientViewMapping
import com.perforce.p4java.core.IChangelist
import com.perforce.p4java.core.IChangelistSummary
import com.perforce.p4java.core.file.FileSpecBuilder
import com.perforce.p4java.core.file.IFileSpec
import com.perforce.p4java.exception.AccessException
import com.perforce.p4java.exception.RequestException
import com.perforce.p4java.impl.generic.client.ClientView
import com.perforce.p4java.impl.generic.client.ClientView.ClientViewMapping
import com.perforce.p4java.impl.mapbased.client.Client
import com.perforce.p4java.impl.mapbased.client.ClientSummary
import com.perforce.p4java.impl.mapbased.server.Parameters
import com.perforce.p4java.impl.mapbased.server.Server
import com.perforce.p4java.option.client.ParallelSyncOptions
import com.perforce.p4java.option.client.ReconcileFilesOptions
import com.perforce.p4java.option.client.SyncOptions
import com.perforce.p4java.option.client.UnshelveFilesOptions
import com.perforce.p4java.option.server.DeleteClientOptions
import com.perforce.p4java.option.server.GetChangelistsOptions
import com.perforce.p4java.option.server.TrustOptions
import com.perforce.p4java.server.CmdSpec
import com.perforce.p4java.server.IOptionsServer
import com.perforce.p4java.server.IServer
import com.perforce.p4java.server.IServerAddress
import com.perforce.p4java.server.ServerFactory.getOptionsServer
import com.tencent.bk.devops.p4sync.task.constants.NONE
import com.tencent.bk.devops.p4sync.task.p4.callback.ReconcileStreamCallback
import com.tencent.bk.devops.p4sync.task.p4.callback.SyncStreamCallback
import com.tencent.bk.devops.p4sync.task.p4.callback.UnshelveStreamCallback
import org.apache.commons.lang3.ArrayUtils
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.Properties

class P4Client(
    // p4java://localhost:1666"
    val uri: String,
    val userName: String,
    val password: String? = null,
    charsetName: String = NONE,
    properties: Properties,
) : AutoCloseable {
    private val server: IOptionsServer = getOptionsServer(uri, properties)
    private val logger = LoggerFactory.getLogger(P4Client::class.java)

    init {
        server.userName = userName
        if (uri.startsWith(IServerAddress.Protocol.P4JAVASSL.toString())) {
            server.addTrust(TrustOptions().setAutoAccept(true))
        }
        server.connect()
        setCharset(charsetName)
        login()
    }

    fun sync(
        client: IClient,
        syncOptions: SyncOptions,
        parallelSyncOptions: ParallelSyncOptions,
        fileSpecs: List<IFileSpec>?,
        keepGoingOnError: Boolean
    ): Boolean {
        setClient(client)
        val callback = SyncStreamCallback(client.server, keepGoingOnError)
        if (parallelSyncOptions.needParallel()) {
            client.syncParallel2(
                fileSpecs = fileSpecs,
                syncOpts = syncOptions,
                pSyncOpts = parallelSyncOptions,
                callback = callback
            )
        }
        client.sync(fileSpecs, syncOptions, callback, 0)
        return !callback.hasFailure()
    }

    private fun IClient.syncParallel2(
        syncOpts: SyncOptions,
        pSyncOpts: ParallelSyncOptions,
        fileSpecs: List<IFileSpec>?,
        callback: SyncStreamCallback
    ) {
        if (server.currentClient == null ||
            !server.currentClient.name.equals(this.name, ignoreCase = true)
        ) {
            throw RequestException(
                "Attempted to sync a client that is not the server's current client"
            )
        }

        val syncOptions =
            buildParallelOptions(serverImpl = server, fileSpecs = fileSpecs, syncOpts = syncOpts, pSyncOpts = pSyncOpts)

        (server as Server).execStreamingMapCommand(
            CmdSpec.SYNC.toString(),
            syncOptions, null, callback, 0, pSyncOpts.callback
        )
    }

    private fun buildParallelOptions(
        syncOpts: SyncOptions,
        pSyncOpts: ParallelSyncOptions,
        fileSpecs: List<IFileSpec>?,
        serverImpl: IServer
    ): Array<out String>? {
        val parallelOptionsBuilder = StringBuilder()
        parallelOptionsBuilder.append("--parallel=")
        if (pSyncOpts.numberOfThreads > 0) {
            parallelOptionsBuilder.append("threads=" + pSyncOpts.numberOfThreads)
        } else {
            parallelOptionsBuilder.append("threads=0")
        }
        if (pSyncOpts.minimum > 0) {
            parallelOptionsBuilder.append(",min=" + pSyncOpts.minimum)
        }
        if (pSyncOpts.minumumSize > 0) {
            parallelOptionsBuilder.append(",minsize=" + pSyncOpts.minumumSize)
        }
        if (pSyncOpts.batch > 0) {
            parallelOptionsBuilder.append(",batch=" + pSyncOpts.batch)
        }
        if (pSyncOpts.batchSize > 0) {
            parallelOptionsBuilder.append(",batchsize=" + pSyncOpts.batchSize)
        }

        val syncOptions =
            Parameters.processParameters(syncOpts, fileSpecs, serverImpl)
        val parallelOptions = parallelOptionsBuilder.toString()
        return ArrayUtils.addAll(syncOptions, parallelOptions)
    }

    private fun ParallelSyncOptions.needParallel(): Boolean {
        return (batch + batchSize + minimum + minumumSize + numberOfThreads) > 0
    }

    fun getClient(clientName: String): IClient? {
        return server.getClient(clientName)
    }

    fun createClient(workspace: Workspace): IClient {
        val client = buildClient(workspace)
        val result = server.createClient(client)
        logger.info(result)
        return client
    }

    private fun buildClient(workspace: Workspace): Client {
        with(workspace) {
            val summary = ClientSummary()
            summary.name = name
            summary.description = description
            summary.root = root
            summary.lineEnd = lineEnd
            summary.options = options
            summary.setServer(server)
            summary.stream = stream
            summary.ownerName = server.userName
            summary.hostName = InetAddress.getLocalHost().hostName
            val client = Client(summary, server, false)
            // 非流仓库时，使用ws view
            if (stream == null && mappings != null) {
                val clientView = ClientView()
                clientView.client = client
                val viewMappings: MutableList<IClientViewMapping> = ArrayList()
                for ((i, mapping) in mappings.withIndex()) {
                    viewMappings.add(ClientViewMapping(i, mapping))
                }
                clientView.entryList = viewMappings
                client.clientView = clientView
            }
            return client
        }
    }

    private fun setClient(client: IClient) {
        server.currentClient = client
        server.workingDirectory = client.root
    }

    fun deleteClient(name: String, isForce: Boolean = false): String {
        val deleteClientOptions = DeleteClientOptions(isForce)
        return server.deleteClient(name, deleteClientOptions)
    }

    fun unshelve(id: Int, client: IClient) {
        if (!isLogin()) {
            login()
        }
        val unshelveFilesOptions = UnshelveFilesOptions(false, false)
        client.unshelveChangelist0(
            id, null,
            0, unshelveFilesOptions
        )
    }

    private fun IClient.unshelveChangelist0(
        sourceChangelistId: Int,
        fileSpecs: List<IFileSpec>?,
        targetChangelistId: Int,
        opts: UnshelveFilesOptions
    ) {
        if (sourceChangelistId <= 0) {
            throw RequestException(
                "Source changelist ID must be greater than zero"
            )
        }

        val sourceChangelistString = "-s$sourceChangelistId"
        var targetChangelistString: String? = null
        if (targetChangelistId == IChangelist.DEFAULT) {
            targetChangelistString = "-cdefault"
        } else if (targetChangelistId > 0) {
            targetChangelistString = "-c$targetChangelistId"
        }

        val serverImpl = server as Server
        serverImpl.execStreamingMapCommand(
            CmdSpec.UNSHELVE.toString(),
            Parameters.processParameters(
                opts,
                fileSpecs,
                arrayOf(
                    sourceChangelistString,
                    targetChangelistString
                ),
                serverImpl
            ),
            null, UnshelveStreamCallback(serverImpl), 0
        )
    }

    fun setCharset(charsetName: String) {
        if (server.supportsUnicode() && charsetName != NONE) {
            logger.info("Connection use Charset $charsetName.")
            server.charsetName = charsetName
        } else {
            logger.info("Server not supports unicode,charset $charsetName was ignore.")
        }
    }

    fun getChangeList(max: Int): List<IChangelistSummary> {
        val ops = GetChangelistsOptions()
        ops.maxMostRecent = max
        ops.type = IChangelist.Type.SUBMITTED
        return server.getChangelists(null, ops)
    }

    fun getLastChangeByStream(streamName: String): IChangelistSummary {
        return getChangeListByStream(1, streamName).first()
    }

    fun getChangeListByStream(max: Int, streamName: String): List<IChangelistSummary> {
        val summary = ClientSummary()
        val clientName = "${System.nanoTime()}.tmp"
        summary.stream = streamName
        summary.name = clientName
        summary.description = "Created by landun (拉取p4) plugin"
        val client = Client(summary, server, false)
        try {
            server.createClient(client)
            val ops = GetChangelistsOptions()
            ops.setOptions("-m$max","-ssubmitted","//$clientName/...")
            setClient(client)
            return server.getChangelists(null, ops)
        } finally {
            deleteClient(clientName)
        }
    }

    fun cleanup(client: IClient) {
        val cleanupOpt = ReconcileFilesOptions()
        cleanupOpt.isUpdateWorkspace = true
        cleanupOpt.isUseWildcards = true
        cleanupOpt.outsideAdd = true
        cleanupOpt.outsideEdit = true
        cleanupOpt.isRemoved = true
        val path = "${client.root}/..."
        val files = FileSpecBuilder.makeFileSpecList(path)
        setClient(client)
        client.reconcileFiles(files, cleanupOpt, ReconcileStreamCallback(client.server), 0)
    }

    private fun login() {
        if (isLogin()) {
            logger.info("already logged in：${server.loginStatus}")
            return
        }
        // 插件凭证使用的是用户名+密码类型，且支持ticket和password设置，
        // 所以这里不确定用户设置的是密码还是ticket，
        // 所以先进行密码登录，如果失败，则进行ticket登录
        try {
            server.login(password)
        } catch (e: AccessException) {
            // 触发认证，设置serverId。否则设置ticket的时候会根据serverAddress,
            // 获取时候又根据serverId来获取，导致不匹配，获取不到ticket，认证失败
            server.loginStatus
            server.authTicket = password
        }
        if (!isLogin()) {
            throw AccessException("The login credentials are incorrect and authentication fails！")
        }
        logger.info("login successfully：${server.loginStatus}")
    }

    private fun isLogin(): Boolean {
        val loginStatus = server.loginStatus
        logger.info(loginStatus)
        if (loginStatus.contains("ticket expires")) {
            return true
        }
        if (loginStatus.contains("not necessary")) {
            return true
        }
        if (loginStatus.isEmpty()) {
            return true
        }
        return false
    }

    override fun close() {
        try {
            server.disconnect()
        } catch (ignore: Exception) {
        }
    }
}
