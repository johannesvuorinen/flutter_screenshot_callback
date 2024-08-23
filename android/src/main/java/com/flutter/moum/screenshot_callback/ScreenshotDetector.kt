package com.flutter.moum.screenshot_callback

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Environment
import android.util.Log
import android.content.pm.PackageManager
import java.lang.ref.WeakReference
import android.app.Activity

open class ScreenshotDetector(private val activity: Activity, private val context: Context,
                         private val callback: (name: String) -> Unit) : AppCompatActivity() {

    companion object {
        private const val TAG = "ScreenshotDetector"
        private const val REQUEST_CODE_READ_EXTERNAL_STORAGE_PERMISSION = 3009
    }

    private var contentObserver: ContentObserver? = null

    private val screenCaptureCallback: Any get() {
        return if (Build.VERSION.SDK_INT >= 34) {
            ScreenCaptureCallback {
                Log.d(TAG, "Screenshot detected");
                callback.invoke("screenshot")
            }
        } else {
            Unit
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerScreenCaptureCallback(mainExecutor, screenCaptureCallback as Activity.ScreenCaptureCallback)
        } else {
            if (contentObserver == null) {
                contentObserver = context.contentResolver.registerObserver()
            }
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            unregisterScreenCaptureCallback(screenCaptureCallback as Activity.ScreenCaptureCallback)
        } else {
            contentObserver?.let { context.contentResolver.unregisterContentObserver(it) }
            contentObserver = null
        }
    }

    private fun reportScreenshotsUpdate(uri: Uri) {
        val screenshots: List<String> = queryScreenshots(uri)
        if (screenshots.isNotEmpty()) {
            callback.invoke(screenshots.last());
        }
    }

    @Suppress("DEPRECATION")
    private fun getPublicScreenshotDirectoryName() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_SCREENSHOTS).name
    } else null

    @Suppress("DEPRECATION")
    private fun getFilePathFromContentResolver(uri: Uri): String? {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
                ),
                null,
                null,
                null
            )?.let { cursor ->
                cursor.moveToFirst()
                val path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
                cursor.close()
                return path
            }
        } catch (e: Exception) {
            Log.w(TAG, e.message ?: "")
        }

        return null
    }

    private fun isScreenshotPath(path: String?): Boolean {
        if (path != null) {
            Log.w(TAG, path)
        }
        val lowercasePath = path?.lowercase()
        val screenshotDirectory = getPublicScreenshotDirectoryName()?.lowercase()
        if (lowercasePath?.contains(".pending") == true) {
            return false
        }
        return (screenshotDirectory != null &&
                lowercasePath?.contains(screenshotDirectory) == true) ||
                lowercasePath?.contains("screenshot") == true
    }

    private fun isReadExternalStoragePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private fun queryScreenshots(uri: Uri): List<String> {
        if (!isReadExternalStoragePermissionGranted()) {
            return listOf()
            //ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_READ_EXTERNAL_STORAGE_PERMISSION);
        }

        try {
            val path = getFilePathFromContentResolver(uri)

            path?.let { p ->
                if (isScreenshotPath(p)) {
                    return listOf(p);
                }
                return listOf()
            }

            //listOf(uri.path.toString())
            return listOf()
        } catch (e:Exception){
            return listOf()
        }
    }

    private fun ContentResolver.registerObserver(): ContentObserver {
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let { reportScreenshotsUpdate(it) }
            }
        }
        registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, contentObserver)
        return contentObserver
    }
}
