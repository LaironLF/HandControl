package com.jeka.smartcontrol.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils.SimpleStringSplitter
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnPreDrawListener
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.mediapipe.examples.handlandmarker.HandLandmarkerHelper.Companion.TAG
import com.jeka.smartcontrol.databinding.ActivityMainBinding
import com.jeka.smartcontrol.service.CursorService


class MainActivity : AppCompatActivity(){



    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
//        val content: View = findViewById(android.R.id.content)
//        content.viewTreeObserver.addOnPreDrawListener(
//            object : ViewTreeObserver.OnPreDrawListener {
//                override fun onPreDraw(): Boolean {
//                    return if(checkPermissions()){
//                        content.viewTreeObserver.removeOnPreDrawListener(this)
//                        true
//                    } else {
//                        this@MainActivity.finish()
//                        false
//                    }
//                }
//            }
//        )



        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)




        binding.btnStartHand.setOnClickListener{ _ -> startHandCursor() }
        binding.btnStopHand.setOnClickListener{ _ -> stopHandCursor() }
        binding.btnSettings.setOnClickListener { _ ->
            checkPermissions()
        }
    }

    private fun startHandCursor(){
        if(!checkPermissions()) return
        Intent(applicationContext, CursorService::class.java).also {
            it.action = CursorService.ACTION_START_CURSOR
            startService(it)
        }
    }

    private fun stopHandCursor(){
        if(!checkPermissions()) return
        Intent(applicationContext, CursorService::class.java).also {
            it.action = CursorService.ACTION_STOP_CURSOR
            startService(it)
        }
    }

    private fun checkPermissions() : Boolean{
        if(!isAccessibilityServiceEnabled(applicationContext, CursorService::class.java)){
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return false
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.CAMERA,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
            ),
            0
        )
        return true
    }

    private fun isAccessibilityServiceEnabled(mContext: Context, java: Class<CursorService>, ): Boolean {
        var accessibilityEnabled = 0
        val service = packageName + "/" + java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                mContext.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            Log.v(TAG, "accessibilityEnabled = $accessibilityEnabled")
        } catch (e: SettingNotFoundException) {
            Log.e(
                TAG, "Error finding setting, default accessibility to not found: "
                        + e.message
            )
        }
        val mStringColonSplitter = SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            Log.v(TAG, "***ACCESSIBILITY IS ENABLED*** -----------------")
            val settingValue = Settings.Secure.getString(
                mContext.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    Log.v(
                        TAG,
                        "-------------- > accessibilityService :: $accessibilityService $service"
                    )
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        Log.v(
                            TAG,
                            "We've found the correct setting - accessibility is switched on!"
                        )
                        return true
                    }
                }
            }
        } else {
            Log.v(TAG, "***ACCESSIBILITY IS DISABLED***")
        }
        return false
    }



}