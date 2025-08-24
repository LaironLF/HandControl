package com.jeka.smartcontrol.service

import android.content.ContentValues.TAG
import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.google.android.gms.common.internal.FallbackServiceBroker
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.time.LocalTime
import kotlin.math.abs


class GestureInvoker(
    private var cursorService: CursorService
) {

    // курсор
    private var cursorPos = Cords()

    // ноготочки
    private var thumbFingerTip = Cords() // большой палец
    private var indexFingerTip = Cords() // указательный
    private var middleFingerTip = Cords() // средний
    private var ringFingerTip = Cords() // указательный
    private var pinkyFingerTip = Cords() // мизинец

    // другие части пальцев
    private var indexFingerPip = Cords(0f, 0f) // указательная пипа


    private var CURRENT_CODE = CODE_NONE

    companion object {
        private const val CODE_NONE = 0
        private const val CODE_FINGER = 1
        private const val CODE_RING = 2
        private const val CODE_PINKY = 3
        private const val CODE_MIDDLE = 4
        private const val CODE_FIST = 5
    }

    data class Cords(
        var x: Float = 0f,
        var y: Float = 0f,
        var xMcp: Float = 0f,
        var yMcp: Float = 0f
    ){
        fun update(landmark: NormalizedLandmark, screenWidth: Int, screenHeight: Int){
            x = landmark.x() * screenWidth
            y = landmark.y() * screenHeight
        }
        fun update(cords: Cords){
            x = cords.x
            y = cords.y
        }

        fun updateMCP(landmark: NormalizedLandmark, screenWidth: Int, screenHeight: Int){
            xMcp = landmark.x() * screenWidth
            yMcp = landmark.y() * screenHeight

        }

        fun Float.rangeValue(fromLow: Double, fromHigh: Double, toLow: Double, toHigh: Double): Double {
            return toLow + (this - fromLow) / (fromHigh - fromLow) * (toHigh - toLow)
        }
    }


    fun handleLandMark(
        landmark: MutableList<NormalizedLandmark>,
        screenWidth: Int,
        screenHeight: Int
    ) {

        cursorPos.update(landmark[0], screenWidth, screenHeight)

        // Красим ноготочки, девочки
        thumbFingerTip.update(landmark[4], screenWidth, screenHeight)
        indexFingerTip.update(landmark[8], screenWidth, screenHeight)
        middleFingerTip.update(landmark[12], screenWidth, screenHeight)
        ringFingerTip.update(landmark[16], screenWidth, screenHeight)
        pinkyFingerTip.update(landmark[20], screenWidth, screenHeight)

        // обновляем основы
        indexFingerTip.updateMCP(landmark[5], screenWidth, screenHeight)
        middleFingerTip.updateMCP(landmark[9], screenWidth, screenHeight)
        ringFingerTip.updateMCP(landmark[13], screenWidth, screenHeight)
        pinkyFingerTip.updateMCP(landmark[17], screenWidth, screenHeight)

        //другие части
        indexFingerPip.update(landmark[6], screenWidth, screenHeight)
        Log.d(TAG, "current code: ${CURRENT_CODE}")
        if(CURRENT_CODE == CODE_NONE || CURRENT_CODE == CODE_FIST) handleFistAction()
        if(CURRENT_CODE == CODE_NONE || CURRENT_CODE == CODE_FINGER) handleIndexGesture()
        if(CURRENT_CODE == CODE_NONE || CURRENT_CODE == CODE_MIDDLE) handleMiddleGesture()
        if(CURRENT_CODE == CODE_NONE || CURRENT_CODE == CODE_RING) handleRingGesture()
        if(CURRENT_CODE == CODE_NONE || CURRENT_CODE == CODE_PINKY) handlePinkyGesture()
    }

    private fun handleMiddleGesture(){
        val dx = abs(thumbFingerTip.x - middleFingerTip.x)
        val dy = (thumbFingerTip.y - middleFingerTip.y)
        if(dx < 60 && dy < 60) {
            if(CURRENT_CODE == CODE_NONE){
                CURRENT_CODE = CODE_MIDDLE
                initCursorPos.x = cursorPos.x
                initCursorPos.y = cursorPos.y
            }
//            Log.d(TAG,
//                "handleMiddleGesture: ${initCursorPos.x} ${initCursorPos.y},${cursorPos.x} ${cursorPos.y}  $actionIsInvoked")
            if(!actionIsInvoked && cursorPos.x - initCursorPos.x < -60){
                cursorService.swipe(false)
                actionIsInvoked = true
            }
            if (!actionIsInvoked && cursorPos.x - initCursorPos.x > 60){
                cursorService.swipe(true)
                actionIsInvoked = true
            }
            if(!actionIsInvoked && cursorPos.y - initCursorPos.y < -100){
                cursorService.slide(false)
                actionIsInvoked = true
            }
            if (!actionIsInvoked && cursorPos.y - initCursorPos.y > 100){
                cursorService.slide(true)
                actionIsInvoked = true
            }
            Log.d(TAG, "handleMiddleGesture: ${initCursorPos.x} ${initCursorPos.y},${cursorPos.x} ${cursorPos.y}  $actionIsInvoked")
        }else{
            CURRENT_CODE = CODE_NONE
            actionIsInvoked = false
        }
    }
    private var actionIsInvoked = false
    private val initCursorPos = Cords()

    private fun handlePinkyGesture(){
        val dx = abs(thumbFingerTip.x - pinkyFingerTip.x)
        val dy = (thumbFingerTip.y - pinkyFingerTip.y)
        if(dx < 60 && dy < 60) CURRENT_CODE = CODE_PINKY
        else if (CURRENT_CODE == CODE_PINKY) {
            cursorService.openShtorka()
            CURRENT_CODE = CODE_NONE
        }
    }


    /**
     * Сворачивание окон
     */
    private fun handleFistAction(){
        val cords = listOf(indexFingerTip, middleFingerTip, ringFingerTip, pinkyFingerTip)
        var udx = 0f
        var udy = 0f
        cords.forEach { finger ->
            val dx = abs(finger.xMcp - finger.x)
            val dy = (finger.yMcp - finger.y)
            udx += dx
            udy += dy
        }
        udx /= cords.size
        udy /= cords.size
        if(udx < 60 && udy < 60) {
            if(CURRENT_CODE == CODE_NONE){
                CURRENT_CODE = CODE_FIST
                initHomePressTime = System.currentTimeMillis()
            }
            Log.d(TAG, "handleFistAction: ${System.currentTimeMillis()} - $initHomePressTime == ${System.currentTimeMillis() - initHomePressTime}")
            if (!allIsPressed && System.currentTimeMillis() - initHomePressTime >= 500L){
                cursorService.closeAll()
                allIsPressed = true
            }
            Log.d(TAG, "handleFistAction: ${System.currentTimeMillis()} - $initHomePressTime == ${System.currentTimeMillis() - initHomePressTime}")
            if (!homeIsPressed && System.currentTimeMillis() - initHomePressTime >= homePressTime){
                cursorService.openApps()
                homeIsPressed = true
            }
        }
        else if(CURRENT_CODE == CODE_FIST) {
            if(!homeIsPressed) cursorService.back()
            CURRENT_CODE = CODE_NONE
            homeIsPressed = false
            allIsPressed = false

        }
    }
    private val homePressTime = 2000L
    private var initHomePressTime = 0L
    private var homeIsPressed = false
    private var allIsPressed = false

    private fun handleRingGesture() {
        val dx = abs(thumbFingerTip.x - ringFingerTip.x)
        val dy = (thumbFingerTip.y - ringFingerTip.y)
        if(dx < 60 && dy < 60) {
            Log.d(TAG, "handleRingGesture: $dx, $dy, $initRingFingerPosY")
            if (CURRENT_CODE == CODE_NONE){
                initRingFingerPosY = ringFingerTip.y
                CURRENT_CODE = CODE_RING
            }
            if(initRingFingerPosY - ringFingerTip.y > 30){
                Log.d(TAG, "handleVolumeAction: volume up")
                initRingFingerPosY = ringFingerTip.y
                val audioManager = cursorService.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            if(initRingFingerPosY - ringFingerTip.y < -30){
                initRingFingerPosY = ringFingerTip.y
                Log.d(TAG, "handleVolumeAction: volume down")
                val audioManager = cursorService.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            Log.d(TAG, "handleVolumeAction: true")
        } else CURRENT_CODE = CODE_NONE
    }
    private var initRingFingerPosY: Float = 0f

    /**
     * клик
     */
    private fun handleIndexGesture() {
        val dX = abs(indexFingerPip.x - indexFingerTip.x)
        val dY = (indexFingerPip.y - indexFingerTip.y)
        if (dX < 60 && dY < 60) {
            Log.d(TAG, "handle: dx: $dX, dy: $dY")
            if(CURRENT_CODE == CODE_NONE){
                CURRENT_CODE = CODE_FINGER
                cursorService.click()
            }
        } else {
            CURRENT_CODE = CODE_NONE
        }
    }



}