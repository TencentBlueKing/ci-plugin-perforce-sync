package com.tencent.bk.devops.p4sync.task.p4

import com.perforce.p4java.option.client.SyncOptions
import com.perforce.p4java.server.IServer

data class MoreSyncOptions(
    /**
     * 强制更新 -f
     * */
    val forceUpdate: Boolean = false,

    /**
     * 预览 -n
     * */
    val noUpdate: Boolean = false,
    /**
     * 跳过客户端 -k
     * */
    val clientBypass: Boolean = false,
    /**
     * 跳过服务端 -p
     * */
    val serverBypass: Boolean = false,
    /**
     * 安静模式 -q
     * */
    val quiet: Boolean = false,
    /**
     * 安全检查 -s
     * */
    val safetyCheck: Boolean = false,
    /**
     * 同步最大数量 -m
     * */
    val max: Int = 0
) : SyncOptions(
    forceUpdate, noUpdate, clientBypass,
    serverBypass, safetyCheck
) {
    companion object {
        const val OPTIONS_SPECS = "b:f b:n b:k b:p b:q b:s i:m"
    }

    init {
        super.quiet = quiet
    }

    override fun processOptions(server: IServer?): MutableList<String> {
        return processFields(
            OPTIONS_SPECS, this.forceUpdate,
            this.noUpdate,
            this.clientBypass,
            this.serverBypass,
            this.quiet,
            this.safetyCheck, max
        )
    }
}
