package com.afterlifearchive.medmanager.data.network

sealed class ApiException(message: String) : Exception(message) {
    class Unauthorized : ApiException("認証の有効期限が切れました。")
    class Forbidden : ApiException("この操作を行う権限がありません。")
    class NotFound : ApiException("対象が見つかりません。")
    class Conflict(message: String = "操作が競合しました。最新の状態を確認してください。") : ApiException(message)
    class InsufficientInventory : ApiException("お薬の在庫が不足しています。")
    class PatientLimitExceeded(val limit: Int, val current: Int) :
        ApiException("現在のバージョンで登録できる本人は${limit}人までです。")
    class HistoryRetentionLimit(val cutoffDate: String, val retentionDays: Int) :
        ApiException("履歴の閲覧は直近${retentionDays}日間に制限されています。")
    class Validation(message: String) : ApiException(message)
    class RateLimited : ApiException("操作が続きました。しばらく待ってから、もう一度お試しください。")
    class Network(message: String = "通信できませんでした。接続を確認してください。") : ApiException(message)
    class Server : ApiException("サーバーで問題が発生しました。")
}
