package com.gc.waravi.skyway.popservice

import android.util.Log
import com.sun.mail.imap.IMAPFolder
import jakarta.mail.FolderClosedException
import jakarta.mail.MessagingException

class ImapIdleRunnable(private val emailFolder: IMAPFolder, private var isSupported : Boolean) : Runnable {
    var onErrorHandler: ((e: Exception) -> Unit)? = null

    override fun run() {
        try {
            while (emailFolder.isOpen) {
                if (isSupported) {
                    val f = emailFolder
                    f.idle()
                    println("IDLE done")
                    isSupported = false
                } else {
                    Thread.sleep(KEEP_ALIVE_FREQ) // sleep for freq milliseconds
                    // This is to force the IMAP server to send us
                    // EXISTS notifications.
                    emailFolder.messageCount
                }
            }
        } catch (ex : FolderClosedException){
            Log.e(this::class.java.simpleName, "Folder was closed.")
            onErrorHandler?.invoke(ex)
        }
        catch (mex: MessagingException) {
            Log.e(this::class.java.simpleName, mex.message.toString())
            onErrorHandler?.invoke(mex)
            Thread.sleep(KEEP_ALIVE_FREQ) // sleep for freq milliseconds
            // This is to force the IMAP server to send us
            // EXISTS notifications.
            //emailFolder.messageCount
            Log.d("imap","EXISTS notifications")
        }
        catch(e: InterruptedException){
            Log.e(this::class.java.simpleName, e.message.toString())
        }
    }

    companion object {
        private const val KEEP_ALIVE_FREQ: Long = 3000
    }
}