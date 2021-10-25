package com.tencent.bk.devops.p4sync.task.api

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.p4sync.task.pojo.util.CredentialInfo
import com.tencent.bk.devops.plugin.pojo.Result
import com.tencent.bk.devops.plugin.utils.JsonUtil

class CredentialResourceApi : BaseApi() {
    fun get(credentialId: String, publicKey: String): Result<CredentialInfo> {
        try {
            val path = "/ticket/api/build/credentials/$credentialId?publicKey=${encode(publicKey)}"
            val request = buildGet(path)
            val responseContent = request(request, "获取凭据失败")
            return JsonUtil.to(responseContent, object : TypeReference<Result<CredentialInfo>>() {})
        } catch (e: Throwable) {
            throw RuntimeException(e.message)
        }
    }
}
