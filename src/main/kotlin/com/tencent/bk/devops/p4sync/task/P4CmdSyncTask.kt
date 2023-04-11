package com.tencent.bk.devops.p4sync.task

import com.perforce.p4java.client.IClient
import com.tencent.bk.devops.p4sync.task.p4.P4Client

class P4CmdSyncTask(builder: Builder) : SyncTask(builder) {
    override fun sync(p4Client: P4Client, client: IClient) {
    }
}
