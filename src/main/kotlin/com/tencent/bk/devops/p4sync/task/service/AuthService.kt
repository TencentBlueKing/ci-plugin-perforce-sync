package com.tencent.bk.devops.p4sync.task.service

import com.tencent.bk.devops.p4sync.task.enum.ticket.CredentialType
import com.tencent.bk.devops.p4sync.task.util.CredentialUtils
class AuthService {
    /**
     * 读取凭证信息
     */
    fun getCredentialInfo(credentialId: String): List<String> {
        // 读取凭证
        val (credentialInfo, credentialType) = CredentialUtils.getCredentialWithType(credentialId)
        if (credentialType != CredentialType.USERNAME_PASSWORD) {
            throw IllegalArgumentException(
                "Certificate type invalid[$credentialType],Required " +
                    "type[USERNAME_PASSWORD]",
            )
        }
        return credentialInfo
    }
}
