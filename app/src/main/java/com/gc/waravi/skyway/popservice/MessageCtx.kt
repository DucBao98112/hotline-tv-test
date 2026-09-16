package com.gc.waravi.skyway.popservice

import com.gc.waravi.skyway.popservice.atpop.FilterUtil
import com.gc.waravi.skyway.popservice.atpop.MailContentHelper
import com.gc.waravi.skyway.popservice.models.NotificationHeader
import jakarta.mail.UIDFolder
import jakarta.mail.internet.MimeMessage

private val contentDescriptionLength = 200

fun MimeMessage.toNotificationHeader(folder: UIDFolder, mailBoxId: String) : NotificationHeader {
    val messageID = messageID.replace("<", "").replace(">", "")
    val messageUID = folder.getUID(this)
    val selectID = messageID + messageUID
    var subject = subject ?: ""
    var sendDate = sentDate
    //var senderMail = message.setFrom("postalcode@medionlink.com")
    val extracted = MailContentHelper.extractContent(contentType, content, true)
    val (title, filteringID, reservedArea) = FilterUtil.subjectCheck(subject)

    val headerKind = when (getHeader("X-APOP-MAIL-KIND")) {
        null -> ""
        else -> getHeader("X-APOP-MAIL-KIND").firstOrNull()
    }
    val headerUrl = when (getHeader("X-APOP-CO-URL")) {
        null -> ""
        else -> getHeader("X-APOP-CO-URL").firstOrNull()
    }

    return NotificationHeader(
        messageID = messageID,
        messageUID = messageUID,
        mailBoxId = mailBoxId,
        subject = subject,
        title = title,
        filteringID = filteringID,
        reservedArea = reservedArea,
        sendDate = sendDate,
        contentDescription =
        if (extracted.content.length <= contentDescriptionLength) extracted.content
        else extracted.content.substring(0, contentDescriptionLength),
        selectID = selectID,
        thumbnailURL = extracted.thumbnailURL,
        headerKind = headerKind,
        headerURL = headerUrl
    )
}