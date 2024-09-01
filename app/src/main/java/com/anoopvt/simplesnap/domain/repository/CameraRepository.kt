package com.anoopvt.simplesnap.domain.repository

import android.graphics.Bitmap
import androidx.camera.view.LifecycleCameraController

interface CameraRepository {

    suspend fun takePhoto(controller: LifecycleCameraController): Bitmap?

    suspend fun recordVideo(controller: LifecycleCameraController)
}