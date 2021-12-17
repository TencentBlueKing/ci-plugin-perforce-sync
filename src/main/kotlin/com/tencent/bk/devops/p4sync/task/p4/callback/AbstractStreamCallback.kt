package com.tencent.bk.devops.p4sync.task.p4.callback

import com.perforce.p4java.core.file.FileSpecOpStatus
import com.perforce.p4java.impl.mapbased.server.cmd.ResultListBuilder
import com.perforce.p4java.server.IServer
import com.perforce.p4java.server.callback.IStreamingCallback
import org.slf4j.LoggerFactory

abstract class AbstractStreamCallback(private val server: IServer) : IStreamingCallback {
    private val logger = LoggerFactory.getLogger(AbstractStreamCallback::class.java)

    private fun buildMessage(resultMap: Map<String, Any>): String {
        val depotFile = resultMap["depotFile"]
        val rev = resultMap["rev"]
        val clientFile = resultMap["clientFile"]
        val action = resultMap["action"]
        return "$depotFile#$rev - $action as $clientFile"
    }

    override fun startResults(key: Int): Boolean {
        return true
    }

    override fun endResults(key: Int): Boolean {
        return true
    }

    override fun handleResult(resultMap: MutableMap<String, Any>, key: Int): Boolean {
        val fileSpec = ResultListBuilder.handleFileReturn(resultMap, server)
        if (fileSpec.opStatus == FileSpecOpStatus.VALID) {
            val msg = buildMessage(resultMap)
            logger.info("Task ${taskName()}: $msg")
        } else {
            logger.error("Task ${taskName()}: ${fileSpec.statusMessage}")
        }
        return true
    }

    abstract fun taskName(): String
}
