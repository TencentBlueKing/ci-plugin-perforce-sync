package com.tencent.bk.devops.p4sync.task.p4

import com.perforce.p4java.server.callback.IProgressCallback
import com.tencent.bk.devops.p4sync.task.util.HumanReadable
import org.slf4j.LoggerFactory

class ProcessCallBack : IProgressCallback {
    private val logger = LoggerFactory.getLogger(ProcessCallBack::class.java)
    private var total = 0
    private var process = 0
    private val totalFileSizePrefix = "... totalFileSize "
    private val totalFileCountPrefix = "... totalFileCount "
    override fun start(key: Int) {
        if (ignoreTask(key)) {
            return
        }
        logger.info("Start task ${taskName(key)}")
    }

    override fun tick(key: Int, tickMarker: String?): Boolean {
        tickMarker?.let {
            val msg = totalSizeReadable(tickMarker) ?: tickMarker
            msg.lines().filter { it.isNotEmpty() }.forEach {
                val taskName = taskName(key)
                if (taskName == SYNC && !it.startsWith(totalFileCountPrefix) &&
                    !it.startsWith(totalFileSizePrefix) &&
                    total > 0
                ) {
                    logger.info("Task $taskName ${++process}/$total: $it")
                } else {
                    logger.info("Task $taskName: $it")
                }
            }
        }
        return true
    }

    override fun stop(key: Int) {
        if (ignoreTask(key)) {
            return
        }
        logger.info("Stop task ${taskName(key)}")
    }

    private fun totalSizeReadable(tickMarker: String): String? {
        val firstLine = tickMarker.lines().first()
        if (firstLine.startsWith(totalFileSizePrefix)) {
            setTotal(tickMarker)
            val bytes = firstLine.substringAfter(totalFileSizePrefix)
            val readableSize = HumanReadable.size(bytes.toLong())
            val newFirstLine = totalFileSizePrefix + readableSize
            return tickMarker.replace(firstLine, newFirstLine)
        }
        return null
    }

    private fun setTotal(tickMarker: String) {
        tickMarker.lines().first { it.startsWith(totalFileCountPrefix) }.let {
            val totalStr = it.split(" ").last()
            total = totalStr.toInt()
        }
    }

    private fun taskName(key: Int): String {
        return TASK_KEY_MAP[key] ?: "Unknown"
    }

    private fun ignoreTask(key: Int): Boolean {
        if (taskName(key) == "Unknown") {
            return true
        }
        return false
    }

    companion object {
        const val SYNC = "SYNC"
        const val UNSHELVE = "UNSHELVE"
        const val CREATE_CLIENT = "CREATE_CLIENT"

        // key由Server生成的执行命令的integer序号，为了增加可读性这里进行了替换。
        val TASK_KEY_MAP = mutableMapOf<Int, String>()
        val TASK_KEY_REVERSE_MAP = mutableMapOf<String, Int>()
        fun addKey(key: Int, name: String) {
            TASK_KEY_MAP[key] = name
            TASK_KEY_REVERSE_MAP[name] = key
        }
    }
}
