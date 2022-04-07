package com.tencent.bk.devops.p4sync.task.p4.callback

import com.perforce.p4java.server.IServer

class UnshelveStreamCallback(server: IServer) : AbstractStreamCallback(server) {
    override fun taskName(): String {
        return "UNSHELVE"
    }

    override fun buildMessage(resultMap: Map<String, Any>): String {
        val depotFile = resultMap["depotFile"]
        val rev = resultMap["rev"]
        val action = resultMap["action"]
        return "$depotFile#$rev - unshelved, opened for $action"
    }
}
