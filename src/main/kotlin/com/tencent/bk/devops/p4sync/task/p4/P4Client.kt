package com.tencent.bk.devops.p4sync.task.p4

import com.perforce.p4java.client.IClient
import com.perforce.p4java.client.IClientViewMapping
import com.perforce.p4java.core.IChangelistSummary
import com.perforce.p4java.core.file.IFileSpec
import com.perforce.p4java.exception.AccessException
import com.perforce.p4java.exception.RequestException
import com.perforce.p4java.impl.generic.client.ClientView
import com.perforce.p4java.impl.generic.client.ClientView.ClientViewMapping
import com.perforce.p4java.impl.mapbased.client.Client
import com.perforce.p4java.impl.mapbased.client.ClientSummary
import com.perforce.p4java.impl.mapbased.server.Parameters
import com.perforce.p4java.impl.mapbased.server.Server
import com.perforce.p4java.impl.mapbased.server.cmd.ResultListBuilder
import com.perforce.p4java.option.client.ParallelSyncOptions
import com.perforce.p4java.option.client.SyncOptions
import com.perforce.p4java.option.server.DeleteClientOptions
import com.perforce.p4java.option.server.GetChangelistsOptions
import com.perforce.p4java.option.server.TrustOptions
import com.perforce.p4java.server.CmdSpec
import com.perforce.p4java.server.IOptionsServer
import com.perforce.p4java.server.IServer
import com.perforce.p4java.server.IServerAddress
import com.perforce.p4java.server.ServerFactory.getOptionsServer
import com.tencent.bk.devops.p4sync.task.constants.NONE
import org.apache.commons.lang3.ArrayUtils
import org.slf4j.LoggerFactory

class P4Client(
    // p4java://localhost:1666"
    val uri: String,
    val userName: String,
    val password: String? = null,
    charsetName: String = NONE
) : AutoCloseable {
    private val server: IOptionsServer = getOptionsServer(uri, null)
    private val logger = LoggerFactory.getLogger(P4Client::class.java)
    private var processKey: Int

    init {
        server.userName = userName
        if (uri.startsWith(IServerAddress.Protocol.P4JAVASSL.toString())) {
            server.addTrust(TrustOptions().setAutoAccept(true))
        }
        server.connect()

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
        if (!server.loginStatus.contains("ticket expires")) {
            throw AccessException("登录凭证错误，认证失败！")
        }
        setCharset(charsetName)
        server.registerProgressCallback(ProcessCallBack())
        processKey = 3
    }

    fun sync(
        client: IClient,
        syncOptions: SyncOptions,
        parallelSyncOptions: ParallelSyncOptions,
        fileSpecs: List<IFileSpec>?
    ): List<IFileSpec> {
        processKey++
        ProcessCallBack.addKey(processKey, ProcessCallBack.SYNC)
        setClient(client)
        if (parallelSyncOptions.needParallel()) {
            return client.syncParallel2(fileSpecs = fileSpecs, syncOpts = syncOptions, pSyncOpts = parallelSyncOptions)
                .toList()
        }
        return client.sync(fileSpecs, syncOptions).toList()
    }

    private fun IClient.syncParallel2(
        syncOpts: SyncOptions,
        pSyncOpts: ParallelSyncOptions,
        fileSpecs: List<IFileSpec>?
    ): MutableList<IFileSpec> {
        val specList: MutableList<IFileSpec> = ArrayList()

        if (server.currentClient == null ||
            !server.currentClient.name.equals(this.name, ignoreCase = true)
        ) {
            throw RequestException(
                "Attempted to sync a client that is not the server's current client"
            )
        }

        val syncOptions =
            buildParallelOptions(serverImpl = server, fileSpecs = fileSpecs, syncOpts = syncOpts, pSyncOpts = pSyncOpts)

        val resultMaps: List<Map<String, Any>> = (server as Server).execMapCmdList(
            CmdSpec.SYNC.toString(),
            syncOptions, null, pSyncOpts.callback
        )

        for (map in resultMaps) {
            specList.add(ResultListBuilder.handleFileReturn(map, server))
        }
        return specList
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
        processKey++
        return server.getClient(clientName)
    }

    fun createClient(workspace: Workspace): IClient {
        processKey++
        ProcessCallBack.addKey(processKey, ProcessCallBack.CREATE_CLIENT)
        val client = buildClient(workspace)
        server.createClient(client)
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

    fun unshelve(id: Int, client: IClient): List<IFileSpec> {
        processKey++
        ProcessCallBack.addKey(processKey, ProcessCallBack.UNSHELVE)
        return client.unshelveChangelist(
            id, null,
            0, false, false
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
        return server.getChangelists(null, ops)
    }

    override fun close() {
        try {
            server.disconnect()
        } catch (ignore: Exception) {
        }
    }
}
