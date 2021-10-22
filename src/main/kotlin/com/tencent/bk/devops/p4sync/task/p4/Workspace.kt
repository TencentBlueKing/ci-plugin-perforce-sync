package com.tencent.bk.devops.p4sync.task.p4

data class Workspace(
    val name: String,
    val description: String,
    val root: String,
    val mappings: List<String>
)
