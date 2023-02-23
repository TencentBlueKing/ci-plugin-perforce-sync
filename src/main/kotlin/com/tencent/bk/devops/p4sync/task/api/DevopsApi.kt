/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bk.devops.p4sync.task.api

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.p4sync.task.pojo.CodeP4Repository
import com.tencent.bk.devops.p4sync.task.pojo.CommitData
import com.tencent.bk.devops.p4sync.task.pojo.PipelineBuildMaterial
import com.tencent.bk.devops.p4sync.task.pojo.RepositoryConfig
import com.tencent.bk.devops.plugin.pojo.Result
import com.tencent.bk.devops.plugin.utils.JsonUtil

/**
 * 蓝盾接口API
 */
class DevopsApi : BaseApi() {
    /**
     * 保存原材料
     */
    fun saveBuildMaterial(materialList: List<PipelineBuildMaterial>): Result<Int> {
        val path = "/process/api/build/repository/saveBuildMaterial"
        val request = buildPost(path, getJsonRequest(materialList), mutableMapOf())
        val responseContent = request(request, "添加源材料信息失败")
        return JsonUtil.to(responseContent, object : TypeReference<Result<Int>>() {})
    }

    /**
     * 保存提交信息
     */
    fun addCommit(commits: List<CommitData>): Result<Int> {
        val path = "/repository/api/build/commit/addCommit"
        val request = buildPost(path, getJsonRequest(commits), mutableMapOf())
        val responseContent = request(request, "添加代码库commit信息失败")
        return JsonUtil.to(responseContent, object : TypeReference<Result<Int>>() {})
    }

    /***
     * 获取代码库信息
     */
    fun getRepository(repositoryConfig: RepositoryConfig): Result<CodeP4Repository> {
        try {
            val path = "/repository/api/build/repositories?" +
                "repositoryId=${repositoryConfig.getURLEncodeRepositoryId()}&" +
                "repositoryType=${repositoryConfig.repositoryType.name}"
            val request = buildGet(path)
            val responseContent = request(request, "获取代码库失败")
            return JsonUtil.to(responseContent, object : TypeReference<Result<CodeP4Repository>>() {})
        } catch (ignore: Exception) {
            throw ignore
        }
    }
}
