package com.gc.waravi.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.gc.waravi.BuildConfig
import com.gc.waravi.R
import java.io.File
import java.util.Timer


object DownloadController {

    private const val FILE_BASE_PATH = "file://"
    private const val MIME_TYPE = "application/vnd.android.package-archive"
    private const val PROVIDER_PATH = ".provider"
    private const val APP_INSTALL_PATH = "\"application/vnd.android.package-archive\""

    fun enqueueDownload(context: Context, resultLauncher: ActivityResultLauncher<Intent>? = null) {
        val apkName = BuildConfig.ApkName
        val url = Constant.NetWork.APK_DOWNLOAD_URL + apkName
        val destination =
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/" + apkName

        val uri = Uri.parse("$FILE_BASE_PATH$destination")

        val file = File(destination)
        if (file.exists()) file.delete()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadUri = Uri.parse(url)
        val request = DownloadManager.Request(downloadUri)
        request.setMimeType(MIME_TYPE)
        request.setTitle(context.getString(R.string.dialog_download_title))
        request.setDescription(context.getString(R.string.dialog_download_message))
        // set destination
        request.setDestinationUri(uri)

        // Enqueue a new download and same the referenceId
        val downloadId = downloadManager.enqueue(request)
        var isCancel = false

        val dialog = createDialog(context, context.getString(R.string.dialog_download_title)
            , context.getString(R.string.dialog_download_message), context.getString(R.string.btn_cancel)){
            downloadManager.remove(downloadId)
            checkAndDeleteApkFile(context)
            isCancel = true
        }
        dialog.show()

        val checkCompleteTimer = Timer()

        fun onDownloadComplete(){
            checkCompleteTimer.cancel()
            dialog.dismiss()
            if (isCancel){
                return
            }
            if(File(destination).exists()){
                install(context, destination, uri, resultLauncher)
            } else{
                val failureDialog = createDialog(context, context.getString(R.string.dialog_download_title)
                    , context.getString(R.string.dialog_update_failure), context.getString(R.string.btn_ok)
                ) { }
                failureDialog.show()
            }
        }

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent
            ){
                context.unregisterReceiver(this)
                onDownloadComplete()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else{
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

//        checkCompleteTimer.schedule(object : TimerTask(){
//            override fun run() {
//                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
//                if (isDownloadSuccessful(cursor)){
//                    context.unregisterReceiver(onComplete)
//                    onDownloadComplete()
//                }
//            }
//        }, 1000L)
    }

//    private fun isDownloadSuccessful(cursor: Cursor? = null): Boolean{
//        if (cursor != null && cursor.moveToNext()) {
//            val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
//            val status = cursor.getInt(columnIndex)
//            cursor.close()
//            return status == DownloadManager.STATUS_SUCCESSFUL
//        }
//        return false
//    }

    private fun createDialog(context: Context, title: String, message: String, buttonText : String?,
                             action: () -> Unit) : AlertDialog{
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonText) { dialog, _ ->
                action()
                dialog.dismiss()
            }
            .create()
    }

    private fun install(context: Context,
                        destination: String,
                        uri: Uri, resultLauncher: ActivityResultLauncher<Intent>? = null){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val contentUri = FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + PROVIDER_PATH,
                File(destination)
            )
            val install = Intent(Intent.ACTION_VIEW)
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            install.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            install.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            install.data = contentUri
            if(resultLauncher != null){
                install.putExtra(Intent.EXTRA_RETURN_RESULT, true)
                resultLauncher.launch(install)
            } else{
                context.startActivity(install)
            }
        } else {
            val install = Intent(Intent.ACTION_VIEW)
            install.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            install.setDataAndType(
                uri,
                APP_INSTALL_PATH
            )
            context.startActivity(install)
            // finish()
        }
    }

    fun checkAndDeleteApkFile(context: Context){
        val destination =
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/" + BuildConfig.ApkName
        val file = File(destination)
        if (file.exists()){
            file.deleteRecursively()
        }
    }

}