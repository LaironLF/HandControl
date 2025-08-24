package com.jeka.smartcontrol.service

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.examples.handlandmarker.HandLandmarkerHelper
import com.google.mediapipe.examples.handlandmarker.HandLandmarkerHelper.Companion.TAG
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.jeka.smartcontrol.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HandDetectionService: LifecycleService(), HandLandmarkerHelper.LandmarkerListener {

    private var screenHeight: Int = 0
    private var screenWidth: Int = 0
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private var imageAnalyzer: ImageAnalysis? = null
    private val listeners = mutableListOf<HandDetectionListener>()

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        setupHandLandMarkerHelper()
        startCamera()
        setupDisplayMetrics()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun setupDisplayMetrics() {
        val displayMetrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            screenWidth = windowMetrics.bounds.width() - (insets.left + insets.right)
            screenHeight = windowMetrics.bounds.height() - (insets.top + insets.bottom)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
        }
    }

    private fun setupHandLandMarkerHelper() {
        handLandmarkerHelper = HandLandmarkerHelper(
            context = this,
            runningMode = RunningMode.LIVE_STREAM,
            minHandDetectionConfidence = HandLandmarkerHelper.DEFAULT_HAND_DETECTION_CONFIDENCE,
            minHandTrackingConfidence = HandLandmarkerHelper.DEFAULT_HAND_TRACKING_CONFIDENCE,
            minHandPresenceConfidence = HandLandmarkerHelper.DEFAULT_HAND_PRESENCE_CONFIDENCE,
            maxNumHands = 1,
            currentDelegate = HandLandmarkerHelper.DELEGATE_CPU,
            handLandmarkerHelperListener = this
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build().also {
                        it.setAnalyzer(cameraExecutor) { image ->
                            detectHand(image)
                        }
                    }
            cameraProvider.unbindAll()
            try {
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(ContentValues.TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectHand(imageProxy: ImageProxy) {
        handLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = true
        )
    }

    private fun startForegroundService() {

        val notification: Notification = NotificationCompat.Builder(this, "running_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Камера работает")
            .setContentText("Улыбаемся и машем, господа! Улыбаемся и машем!")
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onError(error: String, errorCode: Int) {
        Log.d(TAG, "onError: $errorCode $error")
    }

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        val results = resultBundle.results.first()
        results.let {
            for (landmark in it.landmarks()){
                TransferChannel.sendData(landmark)
            }

        }
    }

    fun addListener(listener: HandDetectionListener){
        listeners.add(listener)
    }
    fun removeListener(listener: HandDetectionListener){
        listeners.remove(listener)
    }

    private fun notifyListeners(results: HandLandmarkerResult){
        listeners.forEach{
            it.onResultsChanged(results)
        }
    }

    interface HandDetectionListener{
        fun onResultsChanged(results: HandLandmarkerResult)
    }


    companion object {
        const val ACTION_START_CURSOR: String = "start_cursor"
        const val ACTION_STOP_CURSOR: String = "stop_cursor"
    }



}