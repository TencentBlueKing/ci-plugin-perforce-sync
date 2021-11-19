package com.tencent.bk.devops.p4sync.task.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import com.perforce.p4java.client.IClient
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
    val clientName: String? = null,
    /**
     * 仓库规格
     * 用来创建client时的mapping的左边
     * 未指定clientName时生效
     * */
    @JsonProperty("depotSpec")
    val depotSpec: String? = null,
    /**
     * 同步的文件输出路径
     * 未指定clientName时生效
     * */
    @JsonProperty("outPath")
    val outPath: String? = null,
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

    val tempClientName = "temp_" + System.currentTimeMillis()
    val tempRoot: String = outPath ?: System.getProperty("java.io.tmpdir")

    private fun getTempWorkspace(): Workspace {
        val clientRoot = Paths.get(tempRoot, tempClientName)
        Files.createDirectories(clientRoot)
        return Workspace(
            name = tempClientName, description = "create by p4sync",
            root = clientRoot.toString(), mappings = arrayListOf("$depotSpec //$tempClientName/...")
        )
    }

    fun getClient(p4Client: P4Client): IClient {
        return clientName?.let {
            p4Client.getClient(clientName)
                ?: throw IllegalArgumentException("client $clientName 不存在")
        } ?: let {
            val workspace = getTempWorkspace()
            p4Client.createClient(workspace)
        }
    }

    fun isTempClient(): Boolean {
        return clientName == null
    }
}
