package com.tencent.bk.devops.p4sync.task.p4.callback

import com.perforce.p4java.core.file.FileSpecOpStatus
import com.perforce.p4java.impl.mapbased.server.cmd.ResultListBuilder
import com.perforce.p4java.server.IServer
import com.perforce.p4java.server.callback.IStreamingCallback
import org.slf4j.LoggerFactory
import java.lang.StringBuilder

abstract class AbstractStreamCallback(private val server: IServer) : IStreamingCallback {
    private val logger = LoggerFactory.getLogger(AbstractStreamCallback::class.java)

    open fun buildMessage(resultMap: Map<String, Any>): String {
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
        val statusMessage = fileSpec.statusMessage
        when (fileSpec.opStatus) {
            FileSpecOpStatus.VALID -> {
                val msg = buildMessage(resultMap)
                success()
                log(msg, LOG_LEVEL_INFO)
            }
            FileSpecOpStatus.INFO -> log(statusMessage, LOG_LEVEL_INFO)
            FileSpecOpStatus.ERROR,
            FileSpecOpStatus.CLIENT_ERROR -> log(statusMessage, LOG_LEVEL_ERROR)
            else -> log(mapToString(resultMap), LOG_LEVEL_WARN)
        }

        return true
    }
    open fun success() {}

    protected fun mapToString(resultMap: Map<String, Any>): String {
        val builder = StringBuilder()
        resultMap.forEach {
            builder.append("${it.key}=${it.value} ")
        }
        return builder.toString()
    }

    fun prefix(): String {
        return "Task ${taskName()}:"
    }

    open fun normalMessages(): List<String> = emptyList()

    private fun log(msg: String, level: Int) {
        val prefix = prefix()
        when (level) {
            LOG_LEVEL_INFO -> logger.info("$prefix $msg")
            LOG_LEVEL_ERROR -> {
                if (normalMessages().contains(msg)) {
                    logger.info("$prefix $msg")
                } else {
                    logger.error("$prefix $msg")
                }
            }
        }
    }

    abstract fun taskName(): String

    companion object {
        const val LOG_LEVEL_INFO = 10
        const val LOG_LEVEL_WARN = 20
        const val LOG_LEVEL_ERROR = 30
    }
}
