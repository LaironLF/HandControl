package com.jeka.smartcontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.Toast
import com.google.mediapipe.examples.handlandmarker.HandLandmarkerHelper.Companion.TAG
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.jeka.smartcontrol.R


class CursorService: AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var cursor: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var cursorMovingThread: CursorMovingThread? = null
    private var gestureInvoker = GestureInvoker(this)

    override fun onCreate() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupDisplayMetrics()
        Toast.makeText(applicationContext, "Cursor service initialized", Toast.LENGTH_SHORT).show()
        super.onCreate()
    }

    override fun onDestroy() {
        Toast.makeText(applicationContext, "Cursor service stopped", Toast.LENGTH_SHORT).show()
        removeCursor()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action){
            ACTION_START_CURSOR -> createCursor()
            ACTION_STOP_CURSOR -> removeCursor()
        }
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



    private fun removeCursor() {
        windowManager.removeView(cursor);
        cursorMovingThread?.stopThread()
        Intent(applicationContext, HandDetectionService::class.java).also {
            stopService(it)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCursor() {

        Intent(applicationContext, HandDetectionService::class.java).also {
            startForegroundService(it)
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT
        )
        cursor = LinearLayout(this)
        cursor?.setBackgroundResource(R.drawable.ic_circle)
        params.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(cursor, params)
        cursorMovingThread = CursorMovingThread(cursor!!, params, windowManager)
        cursorMovingThread?.start()
        TransferChannel.setHandDetectionListener(object : TransferChannel.HandDetectionListener{
            override fun onResultsChanged(results: MutableList<NormalizedLandmark>) {
                move(results)
                gestureInvoker.handleLandMark(results, screenWidth, screenHeight)
            }
        })

    }

    fun move(landmark: MutableList<NormalizedLandmark>){
        val y: Int = landmark[0].y()
            .rangeValue(0.3, 0.8, 0.0, screenHeight.toDouble()).toInt()
        val x: Int = landmark[0].x()
            .rangeValue(0.2, 0.8, 0.0, screenWidth.toDouble()).toInt()
//        cursor?.post {
//            windowManager.updateViewLayout(cursor, params)
//        }
        cursorMovingThread?.setTargetCords(x, y)
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { }

    fun click() {
        val x = params.x + (cursor?.width?.div(2)!!)
        val y = params.y + (cursor?.height?.div(2)!!)
        Log.d(TAG, "click gesture: $x, $y")
        val swipePath = Path()
        swipePath.moveTo(x.toFloat(), y.toFloat())
        val gestureDescription = GestureDescription.Builder()
            .addStroke(StrokeDescription(swipePath, 0, 10))
            .build()
        dispatchGesture(gestureDescription, null, null)

    }

    private var previousX = 0
    private var previousY = 0
    private var isInit = true
    fun press(){
        if (isInit){
            val x = params.x + (cursor?.width?.div(2)!!)
            val y = params.y + (cursor?.height?.div(2)!!)
            previousX = x
            previousY = y
            isInit = false
//            Toast.makeText(applicationContext, "Start pressing", Toast.LENGTH_SHORT).show()
        }
//        Log.d(TAG, "press gesture: $x, $y")
//        val swipePath = Path()
//        swipePath.moveTo(previousX.toFloat(), previousY.toFloat())
//        swipePath.lineTo(x.toFloat(), y.toFloat())
//        val gestureDescription = GestureDescription.Builder()
//            .addStroke(StrokeDescription(swipePath, 0, 10, true))
//            .build()
//        dispatchGesture(gestureDescription, null, null)
//        previousX = x
//        previousY = y
    }

    fun stopPress(holdTime: Long){
        val x = params.x + (cursor?.width?.div(2)!!)
        val y = params.y + (cursor?.height?.div(2)!!)
        Log.d(TAG, "press stop: $previousX, $previousY :  $x, $y")
//        Toast.makeText(applicationContext, "Stop pressing", Toast.LENGTH_SHORT).show()
        val swipePath = Path()
        swipePath.moveTo(previousX.toFloat(), previousY.toFloat())
        swipePath.lineTo(x.toFloat(), y.toFloat())
        val gestureDescription = GestureDescription.Builder()
            .addStroke(StrokeDescription(swipePath, 0, holdTime, false))
            .build()
        dispatchGesture(gestureDescription, null, null)
        isInit = true
    }

    /**
     * @param toRight true - направо, false - налево
     */
    fun swipe(toRight: Boolean){
        val toX: Int = if(toRight) screenWidth/2+200 else screenWidth/2-200
        val fromX: Int = if(!toRight) screenWidth/2+200 else screenWidth/2-200
        Log.d(TAG, "swipe")
        val swipePath = Path()
        swipePath.moveTo(fromX.toFloat(), screenHeight.toFloat()/2)
        swipePath.lineTo(toX.toFloat(), screenHeight.toFloat()/2)
        val gestureDescription = GestureDescription.Builder()
            .addStroke(StrokeDescription(swipePath, 0, 200))
            .build()
        dispatchGesture(gestureDescription, null, null)

    }

    fun slide(toDown: Boolean){
        val toY: Int = if (toDown) screenHeight/2+200 else screenHeight/2-200
        val fromY: Int = if(!toDown) screenHeight/2+200 else screenHeight/2-200
        Log.d(TAG, "slide")
        val swipePath = Path()
        swipePath.moveTo(screenWidth.toFloat()/2, fromY.toFloat())
        swipePath.lineTo(screenWidth.toFloat()/2, toY.toFloat())
        val gestureDescription = GestureDescription.Builder()
            .addStroke(StrokeDescription(swipePath, 0, 500))
            .build()
        dispatchGesture(gestureDescription, null, null)
    }

    fun openShtorka(){
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun openApps(){
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun closeAll(){
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
    fun back(){
        performGlobalAction(GLOBAL_ACTION_BACK)
    }


    fun takePicture(){

    }

    private class CursorMovingThread(
        val cursor: View,
        val params: WindowManager.LayoutParams,
        val windowManager: WindowManager
    ): Thread() {
        private var isRunning = true
        private var targetX = 0
        private var targetY = 0
        private var currentX = params.x
        private var currentY = params.y

        fun setTargetCords(x: Int, y: Int){
            targetX = x
            targetY = y
        }

        fun stopThread() {
            isRunning = false
        }

        override fun run() {
            super.run()
            while (isRunning){
                move()
                sleep(10)
            }
        }
        private fun move() {
            currentX += ((targetX - currentX) * 0.4).toInt()
            currentY += ((targetY- currentY) * 0.4).toInt()
            params.x = currentX
            params.y = currentY
            cursor.post {
                windowManager.updateViewLayout(cursor, params)
            }
        }
    }

    fun Float.rangeValue(fromLow: Double, fromHigh: Double, toLow: Double, toHigh: Double): Double {
        return toLow + (this - fromLow) / (fromHigh - fromLow) * (toHigh - toLow)
    }

    companion object {
        const val ACTION_START_CURSOR: String = "start_cursor"
        const val ACTION_STOP_CURSOR: String = "stop_cursor"
    }

}