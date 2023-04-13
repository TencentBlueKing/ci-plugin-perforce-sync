package com.tencent.bk.devops.p4sync.task

interface SyncTaskListener {
    fun before() {}

    fun after(executeResult: ExecuteResult) {}
}
