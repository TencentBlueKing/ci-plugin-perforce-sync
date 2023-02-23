package com.tencent.bk.devops.p4sync.task.service

import com.tencent.bk.devops.atom.pojo.AtomResult
import com.tencent.bk.devops.atom.pojo.StringData
import com.tencent.bk.devops.p4sync.task.api.DevopsApi
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_P4_REPO_NAME
import com.tencent.bk.devops.p4sync.task.constants.BK_REPO_P4_REPO_PATH
import com.tencent.bk.devops.p4sync.task.enum.RepositoryType
import com.tencent.bk.devops.p4sync.task.enum.ticket.CredentialType
import com.tencent.bk.devops.p4sync.task.pojo.P4SyncParam
import com.tencent.bk.devops.p4sync.task.pojo.RepositoryConfig
import com.tencent.bk.devops.p4sync.task.util.CredentialUtils
import org.slf4j.LoggerFactory

class AuthService(
    private val param: P4SyncParam,
    private val atomResult: AtomResult,
    private val devopsApi: DevopsApi
) {
    /**
     * 读取凭证信息
     */
    fun getCredentialInfo(): List<String> {
        with(param) {
            // 凭证ID
            var credentialId = ticketId
            // 使用别名或ID匹配时需读取代码库信息
            if (repositoryType != RepositoryType.URL.name) {
                // 构建代码库配置
                val repositoryConfig = RepositoryConfig(
                    repositoryHashId = repositoryHashId,
                    repositoryName = repositoryName,
                    repositoryType = RepositoryType.valueOf(repositoryType)
                )
                // 获取代码信息
                val result = devopsApi.getRepository(repositoryConfig)
                if (result.isNotOk() || result.data == null) {
                    logger.error("Fail to get the repositoryInfo($repositoryConfig) because of ${result.message}")
                    throw IllegalArgumentException(result.message!!)
                }
                logger.info("get the repo:${result.data}")
                // 保存代码库别名/URL
                atomResult.data[BK_REPO_P4_REPO_NAME] = StringData(result.data!!.aliasName)
                atomResult.data[BK_REPO_P4_REPO_PATH] = StringData(result.data!!.url)
                credentialId = result.data!!.credentialId
                param.p4port = result.data!!.url
                param.repositoryName = result.data!!.aliasName
            }
            // 读取凭证
            val (credentialInfo, credentialType) = CredentialUtils.getCredentialWithType(credentialId)
            if (credentialType != CredentialType.USERNAME_PASSWORD) {
                throw IllegalArgumentException("凭证错误【$credentialType】，需要用户名+密码类型的凭证")
            }
            return credentialInfo
        }
    }

    private val logger = LoggerFactory.getLogger(AuthService::class.java)
}