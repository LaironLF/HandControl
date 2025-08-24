package com.jeka.smartcontrol.service

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class TransferChannel private constructor(){
    private var handDetectionListener: HandDetectionListener? = null
    private fun updateData(results: MutableList<NormalizedLandmark>){
        handDetectionListener?.onResultsChanged(results)
    }
    interface HandDetectionListener{
        fun onResultsChanged(results: MutableList<NormalizedLandmark>)
    }

    companion object{
        val channel: TransferChannel by lazy { TransferChannel() }
        fun sendData(results: MutableList<NormalizedLandmark>) = channel.updateData(results)
        fun setHandDetectionListener(listener: HandDetectionListener) {
            channel.handDetectionListener = listener
        }

    }
}