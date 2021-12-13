package com.tencent.bk.devops.p4sync.task

import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.AtomResult
import com.tencent.bk.devops.p4sync.task.pojo.P4SyncParam
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class P4SyncTest {

    val p4port = System.getProperty("P4PORT")
    val userName = System.getProperty("P4USER")
    val password = System.getProperty("P4PWD")
    val ticket = ""
    val clientName = System.getProperty("P4CLIENT")
    val stream = "//Test/dev"
    val rootPath = System.getProperty("java.io.tmpdir").plus("tmp").plus("ut")

    private val p4Sync = P4Sync()

    val toCleans = arrayListOf<Path>()
    val anyString = ""

    @AfterEach
    fun afterEach() {
        toCleans.forEach { cleanDir(it) }
//        P4Client("p4java://$p4port", userName, password).deleteClient(clientName, true)
    }

    @DisplayName("使用stream同步仓库")
    @Test
    fun syncByStream() {
        val param = P4SyncParam(
            p4port = p4port,
            clientName = clientName,
            rootPath = "dev",
            stream = stream,
            charsetName = "utf8",
//            forceUpdate = true,
//            unshelveId = 25
        )
        param.bkWorkspace = rootPath
        syncAndCheck(param)
    }

    @DisplayName("使用view同步仓库")
    @Test
    fun syncByView() {
        val param = P4SyncParam(
            p4port = p4port,
            clientName = clientName,
            rootPath = rootPath,
//            forceUpdate = true,
            view = "//demo/... //$clientName/demo/...\n" +
                "//depot/... //$clientName/depot/..."
        )
        syncAndCheck(param)
    }

    @DisplayName("使用全量参数")
    @Test
    fun syncByAllParam() {
        val param = P4SyncParam(
            p4port = p4port,
            clientName = clientName,
            rootPath = rootPath,
            forceUpdate = true,
            stream = stream,
            noUpdate = true,
            clientBypass = true,
//            serverBypass = true,
            quiet = true,
//            safetyCheck = true,
            max = 10,
            batch = 10,
            batchSize = 10,
            minimum = 5,
            minimumSize = 512,
            numberOfThreads = 10,
            allWrite = true,
            clobber = true,
            compress = true,
            locked = true,
            modtime = true,
            rmdir = true,
            lineEnd = "Local"
        )
        syncAndCheck(param)
    }

    @DisplayName("unshelve测试")
    @Test
    fun unshelveTest() {
        val param = P4SyncParam(
            p4port = p4port,
            clientName = clientName,
            rootPath = rootPath,
            stream = stream,
            unshelveId = 527
        )
        syncAndCheck(param)
    }

    private fun syncAndCheck(param: P4SyncParam, ticket: String? = null) {
        val result = AtomResult()
        val credential = ticket ?: password
        p4Sync.syncWithTry(param, result, userName = userName, credential = credential)
        assertEquals(Status.success, result.status)
    }

    private fun cleanDir(path: Path) {
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
                    println("删除文件$file")
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    println("删除目录$dir")
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            }
        )
    }
}
