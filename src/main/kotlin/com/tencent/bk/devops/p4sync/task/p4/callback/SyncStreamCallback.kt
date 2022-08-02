package com.tencent.bk.devops.p4sync.task.p4.callback

import com.perforce.p4java.server.IServer
import com.tencent.bk.devops.p4sync.task.util.HumanReadable
import org.slf4j.LoggerFactory

class SyncStreamCallback(server: IServer, keepGoingOnError: Boolean) :
    AbstractStreamCallback(server, keepGoingOnError) {
    private var totalFileSize = 0L
    private var process = 0
    private var totalFileCount = 0
    private val logger = LoggerFactory.getLogger(SyncStreamCallback::class.java)

    override fun taskName(): String {
        return "SYNC $process/$totalFileCount"
    }

    override fun handleResult(resultMap: MutableMap<String, Any>, key: Int): Boolean {
        if (resultMap["totalFileSize"] != null && resultMap["totalFileCount"] != null) {
            totalFileSize = resultMap["totalFileSize"].toString().toLong()
            totalFileCount = resultMap["totalFileCount"].toString().toInt()
            logger.info("${prefix()} totalFileCount: $totalFileCount, totalFileSize ${HumanReadable.size(totalFileSize)}.")
            return super.handleResult(resultMap, key)
        }
        return super.handleResult(resultMap, key)
    }

    override fun normalMessages(): List<String> {
        return listOf("File(s) up-to-date.")
    }

    override fun success() {
        process++
    }
}
