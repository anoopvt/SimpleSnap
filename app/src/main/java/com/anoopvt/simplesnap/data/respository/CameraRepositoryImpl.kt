package com.anoopvt.simplesnap.data.respository

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import com.anoopvt.simplesnap.R
import com.anoopvt.simplesnap.domain.repository.CameraRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraRepositoryImpl @Inject constructor(private val application: Application) :
    CameraRepository {

    private var recording: Recording? = null

    override suspend fun takePhoto(controller: LifecycleCameraController): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            controller.takePicture(ContextCompat.getMainExecutor(application),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        super.onCaptureSuccess(image)

                        try {
                            val matrix = Matrix().apply {
                                postRotate(image.imageInfo.rotationDegrees.toFloat())
                            }
                            val imageBitmap = Bitmap.createBitmap(
                                image.toBitmap(), 0, 0, image.width, image.height, matrix, true
                            )

                            // Close the image to prevent memory leaks
                            image.close()

                            // Resume the coroutine with the captured bitmap
                            continuation.resume(imageBitmap)

                            // Optionally save the photo in a background thread
                            CoroutineScope(Dispatchers.IO).launch {
                                savePhoto(imageBitmap)
                            }

                        } catch (e: Exception) {
                            // If there's an error, close the image and resume with an exception
                            image.close()
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        // Resume the coroutine with the exception if capturing fails
                        continuation.resumeWithException(exception)
                    }
                })
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun recordVideo(controller: LifecycleCameraController) {
        if (recording != null) {
            recording?.stop()
            recording = null
            return
        }

        val timeInMillis = System.currentTimeMillis()
        val file = File(
            application.filesDir, "${timeInMillis}_video" + ".mp4"
        )


        recording = controller.startRecording(
            FileOutputOptions.Builder(file).build(),
            AudioConfig.create(true),
            ContextCompat.getMainExecutor(application)
        ) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) {
                    recording?.close()
                    recording = null
                } else {
                    CoroutineScope(Dispatchers.IO).launch {
                        saveVideo(file)
                    }
                }
            }
        }
    }

    private suspend fun savePhoto(bitmap: Bitmap) {
        withContext(Dispatchers.IO) {

            val resolver: ContentResolver = application.contentResolver
            val imageCollection =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val appName = application.getString(R.string.app_name)

            val timeInMillis = System.currentTimeMillis()

            val imageContentValues: ContentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${timeInMillis}_image" + ".jpg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/$appName")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpg")
                put(MediaStore.MediaColumns.DATE_TAKEN, timeInMillis)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val imageMediaStoreUri: Uri? = resolver.insert(imageCollection, imageContentValues)

            imageMediaStoreUri?.let { uri ->
                try {
                    resolver.openOutputStream(uri)?.let { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }

                    imageContentValues.clear()
                    imageContentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, imageContentValues, null, null)

                } catch (e: Exception) {
                    e.printStackTrace()
                    resolver.delete(uri, null, null)
                }
            }
        }
    }

    private suspend fun saveVideo(file: File) {
        withContext(Dispatchers.IO) {
            val resolver: ContentResolver = application.contentResolver
            val videoCollection =
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val appName = application.getString(R.string.app_name)

            val timeInMillis = System.currentTimeMillis()

            val videoContentValues: ContentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/$appName")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.DATE_ADDED, timeInMillis / 1000)
                put(MediaStore.MediaColumns.DATE_MODIFIED, timeInMillis / 1000)
                put(MediaStore.MediaColumns.DATE_TAKEN, timeInMillis)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val videoMediaStoreUri: Uri? = resolver.insert(
                videoCollection, videoContentValues
            )

            videoMediaStoreUri?.let { uri: Uri ->
                try {

                    resolver.openOutputStream(uri)?.use { outputStream ->
                        resolver.openInputStream(Uri.fromFile(file))?.use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    videoContentValues.clear()
                    videoContentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, videoContentValues, null, null)

                } catch (e: Exception) {
                    e.printStackTrace()
                    resolver.delete(uri, null, null)
                }
            }
        }
    }
}