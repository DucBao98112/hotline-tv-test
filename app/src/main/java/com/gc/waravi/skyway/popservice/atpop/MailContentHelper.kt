package com.gc.waravi.skyway.popservice.atpop

import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Multipart
import jakarta.mail.internet.MimeMultipart
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URL


class MailContentHelper {
    companion object{
        //region [properties]
        const val thumbnailTargetMinSize = 50 * 1024
        const val thumbnailTargetMaxSize = 500 * 1024

        @Throws(MessagingException::class, IOException::class)
        fun getTextFromMessage(message: Message): String {
            var result = ""
            if (message.isMimeType("text/plain")) {
                result = message.content.toString()
            } else if (message.isMimeType("multipart/*")) {
                val mimeMultipart = message.content as MimeMultipart
                result = getTextFromMimeMultipart(mimeMultipart)
            }
            return result
        }

        @Throws(MessagingException::class, IOException::class)
        private fun getTextFromMimeMultipart(
            mimeMultipart: MimeMultipart
        ): String {
            var result = ""
            val count = mimeMultipart.count
            for (i in 0 until count) {
                val bodyPart = mimeMultipart.getBodyPart(i)
                if (bodyPart.isMimeType("text/plain")) {
                    result = "$result\n${bodyPart.content}".trimIndent()
                    break // without break same text appears twice in my tests
                } else if (bodyPart.isMimeType("text/html")) {
                    val html = bodyPart.content as String
                    result = "$result\n${Jsoup.parse(html).text()}".trimIndent()
                } else if (bodyPart.content is MimeMultipart) {
                    result += getTextFromMimeMultipart(bodyPart.content as MimeMultipart)
                }
            }
            return result
        }

        /**
         * Content-Typeで判断してメールの中身を取り出す。
         *
         * multipart/alternativeの場合はHTMLのみを対象とする。
         *
         * multipart/mixedの場合は再帰的に処理を行い、HTMLとPLAINのデータを繋げる。
         *
         * @param contentType Content-Type
         * @param content Content
         * @param isTextOnly HTMLメールの場合にテキスト部分のみ抜き出すかどうか
         * @return 解析結果
         */
        fun extractContent(
            contentType: String,
            content: Any,
            isTextOnly: Boolean
        ): ExtractingResult {
            var ret = ExtractingResult("", null)

            when {
                contentType.contains("multipart/alternative", true) -> {
                    val multiPart = content as Multipart
                    for (i in 0 until multiPart.count) {
                        val part = multiPart.getBodyPart(i)
                        // HTMLメール用のパートのみ処理する
                        if (part.contentType.contains("html", true)) {
                            ret = this.extractContent(part.contentType, part.content, isTextOnly)
                            break
                        }
                    }
                }
                contentType.contains("multipart/mixed", true) -> {
                    val multiPart = content as Multipart
                    val contents = mutableListOf<String>()
                    var charset: String? = null
                    var isHtml = false
                    var imageURL: String? = null
                    for (i in 0 until multiPart.count) {
                        val part = multiPart.getBodyPart(i)
                        // Multipartの中身を再帰で取得
                        val extracted = this.extractContent(part.contentType, part.content, isTextOnly)
                        if (extracted.content.isNotEmpty()) {
                            contents.add(extracted.content)
                            isHtml = isHtml or extracted.isHtml
                            // charsetは最初のパートのものを採用
                            charset = charset ?: extracted.charSet
                            // サムネイルURLは最初に見つかったものを使用
                            imageURL = imageURL ?: extracted.thumbnailURL
                        }
                    }

                    ret = ExtractingResult(
                        contents.joinTo(buffer = StringBuilder()).toString(),
                        charset,
                        isHtml,
                        imageURL
                    )
                }
                contentType.contains("html", true) -> {
                    val retContent: String
                    var imageURL: String? = null
                    if (isTextOnly) {
                        val extracted = this.extractHtmlBodyText(content.toString())
                        retContent = extracted.first
                        imageURL = extracted.second
                    } else {
                        retContent = content.toString()
                    }
                    ret = ExtractingResult(
                        retContent,
                        Regex("""charset=(\S+)""").find(contentType)?.groupValues?.get(1),
                        true,
                        imageURL
                    )
                }
                contentType.contains("plain", true) -> {
                    ret = ExtractingResult(
                        content.toString(),
                        Regex("""charset=(\S+)""").find(contentType)?.groupValues?.get(1)
                    )
                }
            }

            return ret
        }

        fun extractHtmlBodyText(html: String): Pair<String, String?> {
            val retText: String
            var retImageURL: String? = null

            val parsed = Jsoup.parse(html)

            // サムネイル対象の画像を探す
            val tags = parsed.select("*[src], *[background]")
            for (tag in tags) {
                var url = tag.attr("src")
                if (url.isEmpty()) {
                    url = tag.attr("background")
                }
                if (url.endsWith(".png", true) || url.endsWith(".jpg", true) || url.endsWith(
                        ".jpeg",
                        true
                    )
                ) {
                    if (this.thumbnailTargetMinSize <= this.getFileSize(url) && this.getFileSize(url) <= this.thumbnailTargetMaxSize) {
                        retImageURL = url
                        break
                    }
                }
            }

            retText = parsed.body().text()
            return retText to retImageURL
        }

        fun getFileSize(urlString: String): Int {
            if (urlString.isEmpty()) return 0

            val url = URL(urlString)
            val urlConnection = url.openConnection()
            urlConnection.connect()

            return urlConnection.contentLength
        }
        //endregion

    }
    //region [inner classes]
    /**
     * メール解析結果を格納するためのクラス。
     *
     * @param content 本文
     * @param charSet CharSet
     * @param isHtml HTMLメールかどうか
     * @param thumbnailURL サムネイル画像用URL
     */
    data class ExtractingResult(
        val content: String,
        val charSet: String?,
        val isHtml: Boolean = false,
        val thumbnailURL: String? = null
    )
}