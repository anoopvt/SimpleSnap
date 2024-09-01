package com.anoopvt.simplesnap.presentation

import android.graphics.Bitmap
import androidx.camera.view.LifecycleCameraController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anoopvt.simplesnap.domain.repository.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _lastCapturedPhoto = MutableStateFlow<Bitmap?>(null)
    val lastCapturedPhoto = _lastCapturedPhoto.asStateFlow()

    fun onTakePhoto(controller: LifecycleCameraController) {
        viewModelScope.launch {
            val bitmap = cameraRepository.takePhoto(controller)
            _lastCapturedPhoto.update { bitmap }
            delay(2000)
            _lastCapturedPhoto.update { null }
        }
    }

    fun onRecordVideo(controller: LifecycleCameraController) {

        _isRecording.update { isRecording.value.not() }

        viewModelScope.launch {
            cameraRepository.recordVideo(controller)
        }
    }
}