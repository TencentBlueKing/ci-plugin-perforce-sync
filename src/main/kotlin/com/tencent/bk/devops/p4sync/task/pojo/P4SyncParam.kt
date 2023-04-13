package com.tencent.bk.devops.p4sync.task.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import com.perforce.p4java.client.IClientSummary
import com.perforce.p4java.impl.generic.client.ClientOptions
import com.tencent.bk.devops.atom.pojo.AtomBaseParam
import com.tencent.bk.devops.p4sync.task.constants.NONE
import com.tencent.bk.devops.p4sync.task.enum.RepositoryType
import com.tencent.bk.devops.p4sync.task.p4.Workspace
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths

class P4SyncParam(
    /**
     * p4服务器地址
     * 如: localhost:1666
     * */
    @JsonProperty("p4port")
    var p4port: String = "",
    /**
     * HTTP代理地址
     * */
    @JsonProperty("httpProxy")
    val httpProxy: String? = null,
    /**
     * 用户凭证
     * */
    @JsonProperty("ticketId")
    val ticketId: String = "",
    /**
     * 代码库类型
     * ID: 按代码库选择
     * NAME: 按代码库别名输入
     * URL: 按仓库URL输入
     */
    @JsonProperty("repositoryType")
    var repositoryType: String? = RepositoryType.URL.name,
    /**
     * 按代码库选择
     */
    @JsonProperty("repositoryHashId")
    var repositoryHashId: String? = "",
    /**
     * 按代码库别名输入
     */
    @JsonProperty("repositoryName")
    var repositoryName: String? = "",
    /**
     * 同步文件版本
     * */
    @JsonProperty("fileRevSpec")
    val fileRevSpec: String? = null,
    /**
     * client name
     * 如果为空则创建一个临时client
     * */
    @JsonProperty("clientName")
    val clientName: String? = null,

    /**
     * stream
     * */
    @JsonProperty("stream")
    val stream: String? = null,

    /**
     * Defines the line end options available for text files.
     * */
    @JsonProperty("lineEnd")
    val lineEnd: String? = null,

    /**
     * client options
     * */
    @JsonProperty("allWrite")
    var allWrite: Boolean = false,

    /**
     * client options
     * */
    @JsonProperty("clobber")
    val clobber: Boolean = false,

    /**
     * client options
     * */
    @JsonProperty("compress")
    val compress: Boolean = false,

    /**
     * client options
     * */
    @JsonProperty("locked")
    val locked: Boolean = false,

    /**
     * client options
     * */
    @JsonProperty("modtime")
    val modtime: Boolean = false,

    /**
     * client options
     * */
    @JsonProperty("rmdir")
    val rmdir: Boolean = false,
    /**
     * 仓库规格
     * 用来创建client时的mapping的左边
     * 未指定clientName时生效
     * */
    @JsonProperty("view")
    val view: String? = null,
    /**
     * 同步的文件输出路径
     * 未指定clientName时生效
     * */
    @JsonProperty("rootPath")
    val rootPath: String? = null,
    /**
     * 强制更新 -f
     * */
    @JsonProperty("forceUpdate")
    val forceUpdate: Boolean = false,

    /**
     * 预览 -n
     * */
    @JsonProperty("noUpdate")
    val noUpdate: Boolean = false,
    /**
     * 跳过客户端 -k
     * */
    @JsonProperty("clientBypass")
    val clientBypass: Boolean = false,
    /**
     * 跳过服务端 -p
     * */
    @JsonProperty("serverBypass")
    val serverBypass: Boolean = false,
    /**
     * 安静模式 -q
     * */
    @JsonProperty("quiet")
    val quiet: Boolean = false,
    /**
     * 安全检查 -s
     * */
    @JsonProperty("safetyCheck")
    val safetyCheck: Boolean = false,
    /**
     * 同步最大数量 -m
     * */
    @JsonProperty("max")
    val max: Int = 0,

    /**
     * Specifies the number of files in a batch
     */
    @JsonProperty("batch")
    val batch: Int = 0,
    /**
     * Specifies the the number of bytes in a batch
     */
    @JsonProperty("batchSize")
    val batchSize: Int = 0,
    /**
     * Specifies the minimum number of files in a parallel sync
     */
    @JsonProperty("minimum")
    val minimum: Int = 0,
    /**
     * Specifies the minimum number of bytes in a parallel sync
     */
    @JsonProperty("minimumSize")
    val minimumSize: Int = 0,
    /**
     * Specifies the number of independent network connections to be used during parallelisation
     */
    @JsonProperty("numberOfThreads")
    val numberOfThreads: Int = 0,
    @JsonProperty("unshelveId")
    val unshelveId: Int? = null,
    @JsonProperty("charsetName")
    val charsetName: String = NONE,
    @JsonProperty("autoCleanup")
    val autoCleanup: Boolean = false,
    @JsonProperty("netMaxWait")
    val netMaxWait: Int = 60_1000,

    @JsonProperty("keepGoingOnError")
    val keepGoingOnError: Boolean = false,

) : AtomBaseParam() {
    private val logger = LoggerFactory.getLogger(P4SyncParam::class.java)
    fun getWorkspace(): Workspace {
        val clientRootPath = if (rootPath == null) {
            Paths.get(bkWorkspace)
        } else {
            Paths.get(bkWorkspace, rootPath).normalize()
        }
        Files.createDirectories(clientRootPath)
        logger.info("File saving path: $clientRootPath")
        val cn = if (clientName.isNullOrBlank()) "${System.nanoTime()}.tmp" else clientName
        return Workspace(
            name = cn,
            description = "created by bk-ci plugin(PerforceSync).",
            root = clientRootPath.toString(),
            mappings = view?.lines(),
            stream = if (stream.isNullOrEmpty() || stream.trim().isEmpty()) {
                null
            } else {
                stream
            },
            lineEnd = if (lineEnd.isNullOrEmpty()) {
                IClientSummary.ClientLineEnd.LOCAL
            } else {
                IClientSummary.ClientLineEnd.getValue(lineEnd)
                    ?: throw IllegalArgumentException("LineEnd [$lineEnd] Must enum of: LOCAL,UNIX,MAC,WIN,SHARE")
            },
            options = ClientOptions(
                allWrite,
                clobber,
                compress,
                locked,
                modtime,
                rmdir,
            ),
            charsetName = charsetName,
        )
    }

    fun getFileSpecList(): List<String> {
        fileRevSpec?.let {
            return fileRevSpec.lines()
        }
        return emptyList()
    }
}
