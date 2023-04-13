package com.tencent.bk.devops.p4sync.task.pojo

import com.tencent.bk.devops.p4sync.task.constants.P4_JAVA_SCHEME
import com.tencent.bk.devops.p4sync.task.constants.P4_JAVA_SSL_SCHEME

data class P4Repository(
    val p4port: String,
) {
    fun getDepotUri(): String {
        return if (p4port.startsWith(SSL_PREFIX)) {
            val uri = p4port.substring(SSL_PREFIX.length)
            "$P4_JAVA_SSL_SCHEME$uri}"
        } else {
            "$P4_JAVA_SCHEME$p4port"
        }
    }

    companion object {
        private const val SSL_PREFIX = "ssl:"
    }
}
