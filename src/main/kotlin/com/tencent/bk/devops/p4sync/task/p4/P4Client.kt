package com.tencent.bk.devops.p4sync.task.p4

import com.perforce.p4java.client.IClient
import com.perforce.p4java.core.file.IFileSpec
import com.perforce.p4java.exception.RequestException
import com.perforce.p4java.impl.mapbased.client.Client
import com.perforce.p4java.impl.mapbased.rpc.OneShotServerImpl
import com.perforce.p4java.impl.mapbased.server.Parameters
import com.perforce.p4java.impl.mapbased.server.Server
import com.perforce.p4java.impl.mapbased.server.cmd.ResultListBuilder
import com.perforce.p4java.option.client.ParallelSyncOptions
import com.perforce.p4java.option.client.SyncOptions
import com.perforce.p4java.option.server.DeleteClientOptions
import com.perforce.p4java.server.CmdSpec
import com.perforce.p4java.server.IOptionsServer
import com.perforce.p4java.server.IServer
import com.perforce.p4java.server.ServerFactory.getOptionsServer
import org.apache.commons.lang3.ArrayUtils

class P4Client(
    // p4java://localhost:1666"
    val uri: String,
    val userName: String,
    val password: String? = null,
    val ticket: String? = null,
    val serverId: String? = null
) {
    private val server: IOptionsServer = getOptionsServer(uri, null)

    init {
        server.connect()
        server.userName = userName
        if (password != null) {
            server.login(password)
        } else {
            (server as OneShotServerImpl).serverId = serverId
            server.authTicket = ticket
        }
    }

    fun sync(
        client: IClient,
        syncOptions: SyncOptions,
        parallelSyncOptions: ParallelSyncOptions,
        fileSpecs: List<IFileSpec>?
    ): List<IFileSpec> {
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
        val po = arrayOf(parallelOptionsBuilder.toString())

        return ArrayUtils.addAll(po, *syncOptions)
    }

    private fun ParallelSyncOptions.needParallel(): Boolean {
        return (batch + batchSize + minimum + minumumSize + numberOfThreads) > 0
    }

    fun getClient(clientName: String): IClient? {
        return server.getClient(clientName)
    }

    fun createClient(workspace: Workspace): IClient {
        with(workspace) {
            val client = Client.newClient(
                server, // p4java server object
                name, // client name
                description, // client description
                root, // client root
                mappings.toTypedArray() // client mappings
            )
            server.createClient(client)
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
}
