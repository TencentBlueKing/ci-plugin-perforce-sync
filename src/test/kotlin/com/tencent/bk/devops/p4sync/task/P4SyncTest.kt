package com.tencent.bk.devops.p4sync.task

import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.AtomResult
import com.tencent.bk.devops.p4sync.task.pojo.P4SyncParam
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class P4SyncTest {

    val p4port = ""
    val userName = "root"
    val password = ""
    val ticket = ""
    val clientName = ""

    private val p4Sync = P4Sync()

    val toCleans = arrayListOf<Path>()
    val anyString = ""

    @AfterEach
    fun afterEach() {
        toCleans.forEach { cleanDir(it) }
    }

    @DisplayName("同步整个仓库(使用账号密码，临时client)")
    @Test
    fun syncByPasswordTest() {
        val param = P4SyncParam(
            p4port = p4port,
            depotSpec = "//Test/...",
        )
        syncAndCheckByTemp(param)
    }

    @DisplayName("同步整个仓库(使用ticket，临时client)")
    @Test
    fun syncByTicket() {
        val param = P4SyncParam(
            p4port = p4port,
            depotSpec = "//Test/..."
        )
        syncAndCheckByTemp(param, ticket = ticket)
    }

    @DisplayName("同步整个仓库(使用指定client)")
    @Test
    fun syncByClient() {
        val param = P4SyncParam(
            p4port = p4port,
            clientName = clientName,
            fileRevSpec = "hellot.txt#3"
        )
        syncAndCheck(param)
    }

    @DisplayName("同步文件指定版本")
    @Test
    fun syncByFileRevSpec() {
        val param = P4SyncParam(
            p4port = p4port,
            depotSpec = "//Test/main/...",
            fileRevSpec = "hellot.txt#3"
        )
        syncAndCheckByTemp(param)
    }

    @DisplayName("指定同步文件输出路径")
    @Test
    fun syncWithOutPath() {
        val param = P4SyncParam(
            p4port = p4port,
            depotSpec = "//Test/...",
            outPath = "~/data/p4"
        )
        syncAndCheckByTemp(param)
    }

    @DisplayName("检查客户端配置测试")
    @Test
    fun clientTest() {
        // 缺少客户端信息
        P4SyncParam(
            p4port = p4port,
        ).apply {
            val result = AtomResult()
            p4Sync.checkParam(this, result)
            assertEquals(Status.failure, result.status)
            assertEquals(P4Sync.errorMsgClient, result.message)
        }

        // 缺少resultPath
        P4SyncParam(
            p4port = p4port,
            depotSpec = anyString
        ).apply {
            val result = AtomResult()
            p4Sync.checkParam(this, result)
            assertEquals(Status.failure, result.status)
            assertEquals(P4Sync.errorMsgClient, result.message)
        }

        // 缺少resultPath
        P4SyncParam(
            p4port = p4port,
            outPath = anyString
        ).apply {
            val result = AtomResult()
            p4Sync.checkParam(this, result)
            assertEquals(Status.failure, result.status)
            assertEquals(P4Sync.errorMsgClient, result.message)
        }

        // 使用临时客户端
        P4SyncParam(
            p4port = p4port,
            outPath = anyString,
            depotSpec = anyString
        ).apply {
            val result = AtomResult()
            p4Sync.checkParam(this, result)
            assertEquals(Status.success, result.status)
        }

        // 使用已存在客户端
        P4SyncParam(
            p4port = p4port,
            outPath = anyString,
            depotSpec = anyString
        ).apply {
            val result = AtomResult()
            p4Sync.checkParam(this, result)
            assertEquals(Status.success, result.status)
        }
    }

    private fun syncAndCheckByTemp(param: P4SyncParam, ticket: String? = null) {
        with(param) {
            assertTrue(isTempClient())
            val clientPath = Paths.get(param.tempRoot, param.tempClientName)
            assertFalse(clientPath.toFile().exists())
            toCleans.add(clientPath)
            syncAndCheck(param, ticket)
            val targetPath = Paths.get(param.tempRoot, param.tempClientName)
            assertTrue(targetPath.toFile().exists())
        }
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
