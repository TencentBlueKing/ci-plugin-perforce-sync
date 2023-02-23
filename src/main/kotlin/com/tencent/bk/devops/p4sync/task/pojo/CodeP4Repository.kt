package com.tencent.bk.devops.p4sync.task.pojo

import com.fasterxml.jackson.annotation.JsonProperty

data class CodeP4Repository(
    /**
     * p4服务器地址
     * 如: localhost:1666
     * */
    @JsonProperty("url")
    val url: String,
    /**
     * 凭证ID
     * */
    @JsonProperty("credentialId")
    val credentialId: String,
    /**
     * 项目名
     * */
    @JsonProperty("projectName")
    val projectName: String,
    /**
     * 用户名
     * */
    @JsonProperty("userName")
    val userName: String,
    /**
     * 蓝盾项目ID
     * */
    @JsonProperty("projectId")
    val projectId: String?,
    /**
     * 仓库hash id
     * */
    @JsonProperty("repoHashId")
    val repoHashId: String?,
    /**
     * 代码库别名
     * */
    @JsonProperty("aliasName")
    val aliasName: String
)
