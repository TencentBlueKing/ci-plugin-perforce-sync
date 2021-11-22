package com.tencent.bk.devops.p4sync.task.p4

import com.perforce.p4java.client.IClientSummary

data class Workspace(
    val name: String,
    val description: String,
    val root: String,
    val stream: String? = null,
    val mappings: List<String>?,
    val lineEnd: IClientSummary.ClientLineEnd,
    val options: IClientSummary.IClientOptions
)
