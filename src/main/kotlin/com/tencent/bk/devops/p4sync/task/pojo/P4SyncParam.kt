package com.tencent.bk.devops.p4sync.task.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import com.perforce.p4java.client.IClient
import com.perforce.p4java.client.IClientSummary
import com.perforce.p4java.impl.generic.client.ClientOptions
import com.tencent.bk.devops.atom.pojo.AtomBaseParam
import com.tencent.bk.devops.p4sync.task.p4.P4Client
import com.tencent.bk.devops.p4sync.task.p4.Workspace
import java.nio.file.Files
import java.nio.file.Paths

class P4SyncParam(
    /**
     * p4服务器地址
     * 如: localhost:1666
     * */
    @JsonProperty("p4port")
    val p4port: String,
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
     * 同步文件版本
     * */
    @JsonProperty("fileRevSpec")
    val fileRevSpec: String? = null,
    /**
     * client name
     * 如果为空则创建一个临时client
     * */
    @JsonProperty("clientName")
    val clientName: String,

    /**
     * stream
     * */
    @JsonProperty("stream")
    val stream: String? = null,

    /**
     * Defines the line end options available for text files.
     * */
    @JsonProperty("lineEnd")
    val lineEnd: String? = IClientSummary.ClientLineEnd.LOCAL.toString(),

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
    val rootPath: String,
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
    val numberOfThreads: Int = 0

) : AtomBaseParam() {

    private fun getWorkspace(): Workspace {
        Files.createDirectories(Paths.get(rootPath))
        return Workspace(
            name = clientName, description = "create by p4sync",
            root = rootPath, mappings = view?.lines(),
            stream = stream,
            lineEnd = IClientSummary.ClientLineEnd.getValue(lineEnd)
                ?: throw IllegalArgumentException("Must enum of: LOCAL,UNIX,MAC,WIN,SHARE"),
            options = ClientOptions(
                allWrite, clobber,
                compress, locked, modtime, rmdir
            )
        )
    }

    fun getClient(p4Client: P4Client): IClient {
        return p4Client.getClient(clientName)
            ?: p4Client.createClient(getWorkspace())
    }
}
