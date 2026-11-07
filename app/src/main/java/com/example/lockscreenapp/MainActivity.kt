package com.example.lockscreenapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isAccessibilityServiceEnabled()) {
            // Trigger screen lock using the active service instance
            LockService.instance?.lockDevice()
            finish()
        } else {
            // Guide user to Accessibility Settings to enable the service
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            finish()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        for (service in enabledServices) {
            if (service.id.contains(packageName)) {
                return true
            }
        }
        return false
    }
}