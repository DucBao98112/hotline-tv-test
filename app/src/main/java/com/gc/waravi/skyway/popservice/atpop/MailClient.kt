package com.gc.waravi.skyway.popservice.atpop

import android.util.Log
import com.gc.waravi.BuildConfig
import com.gc.waravi.skyway.popservice.models.EncryptionType
import com.gc.waravi.skyway.popservice.models.MailServerInfo
import com.gc.waravi.skyway.popservice.models.NotificationHeader
import com.gc.waravi.skyway.popservice.toNotificationHeader
import jakarta.mail.*
import jakarta.mail.internet.MimeMessage
import java.util.*


/**
 * メールクライアントクラス。
 *
 * @param appContext アプリケーションコンテキスト
 */
class MailClient {
    private val contentDescriptionLength = 200

    private val MimeMessage.messageIDWithoutBracket: String?
        get() = try {
            this.messageID.replace("<", "").replace(">", "")
        } catch (t: Throwable) {
            null
        }
    //endregion

    //region [public methods]
    /**
     * メールチェックを行う。
     *
     * @param mailServerInfo メールサーバの情報
     * @return メールボックスの中身
     * - Content-Typeを見てメール本文を解析するのに少し時間がかかるため、
     *   送信日時が [MailServerInfo.syncDate] より前のメールの場合は [NotificationHeader.contentDescription] には空文字を設定する
     */
    fun check(mailServerInfo: MailServerInfo): List<NotificationHeader> {
        val ret = mutableListOf<NotificationHeader>()

        this.connect(mailServerInfo) { itPair ->
            for (message in itPair.first) {
                try {
                    val mailHeader = (message as MimeMessage).toNotificationHeader(itPair.second, mailServerInfo.user)
                    ret.add(mailHeader)
                } catch (t: Throwable) {
//                    throw t
                }
            }
        }

        return ret.reversed()
    }

    /**
     * 指定されたメッセージIDのメール本文を取得する。
     *
     * @param mailServerInfo メールサーバの情報
     * @param messageID メッセージID
     * @return
     * - first: メッセージ
     * - second: HTML形式を含むかどうか
     * - third: charset
     */
    fun fetch(
        mailServerInfo: MailServerInfo,
        messageID: String
    ): Triple<String, Boolean, String?>? {
        var ret: Triple<String, Boolean, String?>? = null

        this.connect(mailServerInfo) { messages ->
            val mime = messages.first.singleOrNull {
                (it as MimeMessage).messageIDWithoutBracket + messages.second.getUID(it) == messageID
            }

            ret = when (mime) {
                null -> null
                else -> {
                    val extracted = MailContentHelper.extractContent(mime.contentType, mime.content, false)
                    Triple(extracted.content, extracted.isHtml, extracted.charSet)
                }
            }
        }

        return ret
    }
    //endregion

    //region [private methods]
    /**
     * メールサーバに接続する。
     *
     * @param mailServerInfo メールサーバの情報
     * @param action メールフォルダを開いた後に行う処理
     */
    private fun connect(
        mailServerInfo: MailServerInfo,
        start: Int? = null,
        end: Int? = null,
        action: (Pair<Array<Message>, UIDFolder>) -> Unit
    ) {
        try {
            // create properties field
            val properties = Properties()
            properties["mail.imaps.starttls.enable"] =
                (mailServerInfo.encryption == EncryptionType.Tls)
            properties["mail.imaps.ssl.enable"] = (mailServerInfo.encryption == EncryptionType.Ssl)
            properties["mail.debug"] = BuildConfig.DEBUG
            if (BuildConfig.DEBUG) {
                properties["mail.imaps.ssl.checkserveridentity"] = false
                properties["mail.imaps.ssl.trust"] = "*"
            }
            // Session.getDefaultInstance()は引数を変えて2回目以降に呼び出すと無視されるので使用しない
            val emailSession = Session.getInstance(properties)

            val protocol = when (mailServerInfo.encryption) {
                EncryptionType.None -> "imap"
                else -> "imaps"
            }
            val store = emailSession.getStore(protocol)
            with(mailServerInfo) {
                store.connect(host, port, user, password)
            }

            // create the folder object and open it
            val emailFolder = store.getFolder("INBOX")
            emailFolder.open(Folder.READ_ONLY)

            // retrieve the messages from the folder in an array
            val messages = if (start != null && end != null){
                val maxEnd = emailFolder.messageCount
                val adjustedStart = if (start >= maxEnd) maxEnd else start
                val adjustedEnd = if (end <= maxEnd) end else maxEnd
                Log.d("Lhts", "msg count $maxEnd, $adjustedStart, $adjustedEnd")

                emailFolder.getMessages(adjustedStart, adjustedEnd )
            } else{
                emailFolder.messages
            }
            val uf = emailFolder as UIDFolder

            action(Pair(messages, uf))

            // close the store and folder objects
            emailFolder.close(false)
            store.close()
        } catch (t: Throwable) {
            throw t
        }
    }

    fun fetchInRange(mailServerInfo: MailServerInfo, start: Int, end: Int): List<NotificationHeader> {
        Log.d(this::class.java.simpleName, "fetchInRange Start/End: $start/$end")
        val ret = mutableListOf<NotificationHeader>()

        this.connect(mailServerInfo, start, end) { itPair ->
            for (message in itPair.first) {
                try {
                    val mailHeader = (message as MimeMessage).toNotificationHeader(itPair.second, mailServerInfo.user)
                    ret.add(mailHeader)
                } catch (t: Throwable) {
//                    throw t
                }
            }
        }

        return ret
    }

    public suspend fun send(mailServerInfo: MailServerInfo, subject: String, content: String) {
        // create properties field
        val properties = Properties()
        properties.put("mail.smtp.host", mailServerInfo.host)
        properties.put("mail.smtp.auth", "true")
        properties.put("mail.smtp.port", "465")
        properties.put("mail.smtp.socketFactory.post", "465")
        properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
        properties["mail.debug"] = BuildConfig.DEBUG
        if (BuildConfig.DEBUG) {
            properties["mail.imaps.ssl.checkserveridentity"] = false
            properties["mail.imaps.ssl.trust"] = "*"
        }
        // Session.getDefaultInstance()は引数を変えて2回目以降に呼び出すと無視されるので使用しない
        val session = Session.getInstance(properties)
        try {
            val msg = MimeMessage(session)
            val email = String.format("%s@%s", mailServerInfo.user, mailServerInfo.host)
            msg.setFrom(email)
            msg.setRecipients(
                Message.RecipientType.TO,
                email
            )
            msg.subject = subject
            msg.sentDate = Date()
            msg.setText(content)
            Transport.send(msg, email, mailServerInfo.password)
        } catch (mex: Exception) {
            println("send failed, exception: $mex")
        }
    }
}
