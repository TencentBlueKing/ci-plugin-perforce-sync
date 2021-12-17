package com.tencent.bk.devops.p4sync.task.p4.callback

import com.perforce.p4java.server.IServer

class ReconcileStreamCallback(server: IServer) : AbstractStreamCallback(server) {
    override fun taskName(): String {
        return "AUTO_CLEANUP"
    }

    override fun normalMessages(): List<String> {
        return listOf("no file(s) to reconcile")
    }
}
