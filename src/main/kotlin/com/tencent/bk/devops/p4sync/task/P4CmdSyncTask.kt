package com.tencent.bk.devops.p4sync.task

import com.perforce.p4java.client.IClient
import com.tencent.bk.devops.p4sync.task.p4.P4Client
import com.tencent.bk.devops.p4sync.task.util.P4SyncUtils
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class P4CmdSyncTask(builder: Builder) : SyncTask(builder) {
    override fun sync(p4Client: P4Client, client: IClient) {
        val opts = P4SyncUtils.buildOptions(
            serverImpl = null,
            fileSpecs = fileSpecs,
            syncOpts = syncOptions,
            pSyncOpts = parallelSyncOptions,
        )
        val command = mutableListOf<String>(
            "p4",
            "-c",
            client.name,
            "-p",
            repository.p4port,
            "-C",
            charset,
            "-u",
            userName,
            "sync",
        )
        command.addAll(opts)
        val commandStr = command.joinToString(" ")
        // 为了展示终端shell命令样式，所以这里直接打印到终端，而不通过日志框架。
        println("##[command]# $commandStr")
        val pb = ProcessBuilder()
        pb.command(command)
        pb.redirectErrorStream(true)
        val process = pb.start()
        thread {
            printStream(process.inputStream)
        }
        val ev = process.waitFor()
        if (ev != 0) {
            error("Sync failed,exit: $ev.")
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

    companion object {
        private val logger = LoggerFactory.getLogger(P4CmdSyncTask::class.java)
    }
}
