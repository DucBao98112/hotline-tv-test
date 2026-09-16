package com.gc.waravi.skyway.popservice.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.*

@Entity
data class NotificationHeader(
    /**
     * メッセージID。
     */
    @PrimaryKey
    var messageID: String = "",
    /**
     * タイトル。
     */
    var title: String = "",
    /**
     * セレクトID。
     */
    var selectID: String = "",
    /**
     * UID。
     */
    var messageUID: Long = 0,
    /**
     * MailServerInfoテーブルデータ。
     */
    var mailBoxId: String = "",
    /**
     * 送信日。
     */
    var sendDate: Date = Date(),
    /**
     * 件名。
     */
    var subject: String = "",
    /**
     * フィルタリングID。
     */
    var filteringID: String = "",
    /**
     * 予約領域。
     */
    var reservedArea: String = "",
    /**
     * Content見出し。
     */
    var contentDescription: String = "",
    /**
     * サムネイル用画像のURL。
     */
    var thumbnailURL: String? = null,
    /**
     * メール種別情報「X-APOP-MAIL-KIND」​。
     */
    var headerKind: String? = null,
    /**
     * クーポン利用ページURL情報「X-APOP-CO-URL」。
     */
    var headerURL: String? = null,
    /**
     * メール受信時ユーザー属性。
     */
    var userInfo: String? = null,
    /**
     * 開封済みフラグ。
     */
    var isOpened: Boolean = false,
    /**
     * お気に入りフラグ。
     */
    var isInFavorites: Boolean = false,
    /**
     * 削除フラグ。
     */
    var isDelete: Boolean = false,

) : Serializable