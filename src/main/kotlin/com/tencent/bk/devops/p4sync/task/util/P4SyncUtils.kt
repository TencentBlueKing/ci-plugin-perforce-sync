package com.tencent.bk.devops.p4sync.task.util

import com.perforce.p4java.client.IClient
import com.perforce.p4java.client.IClientViewMapping
import com.perforce.p4java.core.file.IFileSpec
import com.perforce.p4java.impl.generic.client.ClientView
import com.perforce.p4java.impl.mapbased.client.Client
import com.perforce.p4java.impl.mapbased.client.ClientSummary
import com.perforce.p4java.impl.mapbased.server.Parameters
import com.perforce.p4java.option.client.ParallelSyncOptions
import com.perforce.p4java.option.client.SyncOptions
import com.perforce.p4java.server.IServer
import com.tencent.bk.devops.p4sync.task.p4.Workspace
import org.apache.commons.lang3.ArrayUtils
import java.net.InetAddress

object P4SyncUtils {
    fun workspaceToClient(workspace: Workspace, server: IServer): IClient {
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
            buildViewMapping(client, mappings)
            return client
        }
    }

    fun compareClients(c1: IClient, c2: IClient): Boolean {
        if (c1.root != c2.root) {
            return false
        }
        if (c1.isStream != c2.isStream) {
            return false
        }
        if (c1.stream != c2.stream) {
            return false
        }
        if (!c1.isStream && !compareClientView(c1.clientView, c2.clientView)) {
            return false
        }
        if (c1.lineEnd != c2.lineEnd) {
            return false
        }
        if (c1.options.toString() != c2.options.toString()) {
            return false
        }
        if (c1.hostName != c2.hostName) {
            return false
        }
        return true
    }

    private fun compareClientView(v1: ClientView, v2: ClientView): Boolean {
        val e1 = v1.entryList
        val e2 = v2.entryList
        if (e1.size != e2.size) {
            return false
        }
        e1.sortBy { it.depotSpec }
        e2.sortBy { it.depotSpec }
        for (i in 0..e1.lastIndex) {
            if (!compareClientViewMapping(e1[i], e2[i])) {
                return false
            }
        }
        return true
    }

    private fun compareClientViewMapping(m1: IClientViewMapping, m2: IClientViewMapping): Boolean {
        if (m1.depotSpec != m2.depotSpec || m1.client != m2.client) {
            return false
        }
        return true
    }

    fun buildParallelOptions(
        pSyncOpts: ParallelSyncOptions,
    ): String {
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
        return parallelOptionsBuilder.toString()
    }

    fun buildOptions(
        syncOpts: SyncOptions,
        pSyncOpts: ParallelSyncOptions,
        fileSpecs: List<IFileSpec>?,
        serverImpl: IServer?,
    ): Array<out String> {
        val syncOptions = Parameters.processParameters(syncOpts, fileSpecs, serverImpl)
        if (needParallel(pSyncOpts)) {
            val parallelOptions = buildParallelOptions(pSyncOpts)
            return ArrayUtils.addAll(syncOptions, parallelOptions)
        }
        return syncOptions
    }

    fun needParallel(parallelSyncOptions: ParallelSyncOptions): Boolean {
        with(parallelSyncOptions) {
            return (batch + batchSize + minimum + minumumSize + numberOfThreads) > 0
        }
    }

    /**
     * 构建视图映射
     */
    private fun buildViewMapping(client: IClient, mapping: List<String>?) {
        val clientView = ClientView()
        // 视图映射
        val viewMappings: MutableList<IClientViewMapping> = ArrayList()
        clientView.client = client
        // 非流仓库时，使用ws view
        if (client.stream == null && mapping != null) {
            for ((i, value) in mapping.withIndex()) {
                viewMappings.add(ClientView.ClientViewMapping(i, value))
            }
        }
        clientView.entryList = viewMappings
        client.clientView = clientView
    }
}
