package com.afterlifearchive.medmanager.data.network

sealed class ApiException(message: String) : Exception(message) {
    class Unauthorized : ApiException("認証の有効期限が切れました。")
    class Forbidden : ApiException("この操作を行う権限がありません。")
    class NotFound : ApiException("対象が見つかりません。")
    class Conflict(message: String) : ApiException(message)
    class Validation(message: String) : ApiException(message)
    class Network(message: String = "通信できませんでした。接続を確認してください。") : ApiException(message)
    class Server : ApiException("サーバーで問題が発生しました。")
}
