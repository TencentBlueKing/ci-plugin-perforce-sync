package com.tencent.bk.devops.p4sync.task

import com.perforce.p4java.client.IClient
import com.tencent.bk.devops.p4sync.task.p4.P4Client
import com.tencent.bk.devops.p4sync.task.util.P4SyncUtils
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class P4CmdSyncTask(builder: Builder) : SyncTask(builder) {
    override fun sync(p4Client: P4Client, client: IClient) {
        val opts = P4SyncUtils.buildOptions(
            serverImpl = null,
            fileSpecs = fileSpecs,
            syncOpts = syncOptions,
            pSyncOpts = parallelSyncOptions,
        ).joinToString(" ")
        val syncCmd = "p4 -c ${client.name} -p ${repository.p4port} -C $charset -u $userName sync $opts"
        logger.info("# $syncCmd")
        val process = Runtime.getRuntime().exec(syncCmd)
        if (process.waitFor() != 0) {
            printErrorStream(process.errorStream)
            error("Sync failed.")
        } else {
            printStream(process.inputStream)
            // Alert msg(File(s) up-to-date.) is in stderr
            printStream(process.errorStream)
        }
    }

    private fun printStream(inputStream: InputStream, error: Boolean = false) {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        reader.use {
            var line = it.readLine()
            while (line != null) {
                if (error) {
                    logger.error(line)
                } else {
                    logger.info(line)
                }
                line = it.readLine()
            }
        }
    }

    private fun printErrorStream(errorInput: InputStream) {
        printStream(errorInput, true)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(P4CmdSyncTask::class.java)
    }
}
