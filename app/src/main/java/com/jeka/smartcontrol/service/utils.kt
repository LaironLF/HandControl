package com.jeka.smartcontrol.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

fun getAccessibilityServiceComponentName(context: Context): ComponentName? {
    val enabledServices =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        if (componentNameString.contains(context.packageName) && componentNameString.contains(CursorService::class.java.simpleName)) {
            return ComponentName.unflattenFromString(componentNameString)
        }
    }
    return null
}

fun getAccessibilityService(context: Context): CursorService? {
    val componentName = getAccessibilityServiceComponentName(context)
    return componentName?.let { context.getSystemService(it.toString()) as CursorService }
}

