package com.tencent.bk.devops.p4sync.task.constants

val P4_CONFIG_FILE_NAME = ".p4config.cfg"
val P4_USER = "P4USER"
val P4_CLIENT = "P4CLIENT"
val P4_CHARSET = "P4CHARSET"
val P4_PORT = "P4PORT"
val NONE = "none"
const val EMPTY = ""
// 历史changlist最大拉取数量
const val P4_CHANGELIST_MAX_MOST_RECENT = 50

const val BK_CI_P4_DEPOT_HEAD_CHANGE_ID = "BK_CI_P4_REPO_HEAD_CHANGE_ID"
const val BK_CI_P4_DEPOT_HEAD_CHANGE_COMMENT = "BK_CI_P4_DEPOT_HEAD_CHANGE_COMMENT"
const val BK_CI_P4_DEPOT_HEAD_CHANGE_CLIENT_ID = "BK_CI_P4_DEPOT_HEAD_CHANGE_CLIENT_ID"
const val BK_CI_P4_DEPOT_HEAD_CHANGE_USER = "BK_CI_P4_DEPOT_HEAD_CHANGE_USER"
const val BK_CI_P4_DEPOT_LAST_CHANGE_ID = "BK_CI_P4_DEPOT_LAST_CHANGE_ID"
const val BK_CI_P4_DEPOT_WORKSPACE_PATH = "BK_CI_P4_DEPOT_WORKSPACE_PATH"
const val BK_CI_P4_DEPOT_PORT = "BK_CI_P4_DEPOT_PORT"
const val BK_CI_P4_DEPOT_STREAM = "BK_CI_P4_DEPOT_STREAM"
const val BK_CI_P4_DEPOT_CLIENT = "BK_CI_P4_DEPOT_CLIENT"
const val BK_CI_P4_DEPOT_P4_CHARSET = "BK_CI_P4_DEPOT_P4_CHARSET"

const val BK_REPO_TASKID = "bk_repo_taskId_"
const val BK_REPO_CONTAINER_ID = "bk_repo_container_id_"
const val BK_REPO_TYPE = "bk_repo_type_"
const val BK_REPO_TICKET_ID = "bk_repo_ticket_id_"
const val BK_REPO_DEPOT_PORT = "bk_repo_depot_port_"
const val BK_REPO_DEPOT_STREAM = "bk_repo_depot_stream_"
const val BK_REPO_DEPOT_P4_CHARSET = "bk_repo_depot_p4_charset_"
const val BK_REPO_P4_CLIENT_NAME = "bk_repo_p4_client_name_"
const val BK_REPO_LOCAL_PATH = "bk_repo_local_path_"
// 代码库ID
const val BK_REPO_P4_REPO_ID = "BK_REPO_P4_REPO_ID"
// 代码库别名
const val BK_REPO_P4_REPO_NAME = "BK_REPO_P4_REPO_NAME"
// 代码库URL
const val BK_REPO_P4_REPO_PATH = "BK_REPO_P4_REPO_PATH"