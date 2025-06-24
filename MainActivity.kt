package com.example.arabicsignlanguage

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * Main Activity - Entry Point that redirects to HomeActivity
 */
class MainActivity : AppCompatActivity() {
    
    private val TAG = "MainActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== MAIN ACTIVITY - REDIRECTING TO HOME ===")
        
        // Check if we should go to translation selection
        val goToTranslationSelection = intent.getBooleanExtra("GO_TO_TRANSLATION_SELECTION", false)
        Log.d(TAG, "Go to translation selection: $goToTranslationSelection")

        if (goToTranslationSelection) {
            // Redirect to TranslationSelectionActivity
            Log.d(TAG, "Redirecting to TranslationSelectionActivity")
            val intent = Intent(this, TranslationSelectionActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            // Redirect to HomeActivity
            Log.d(TAG, "Redirecting to HomeActivity")
            val intent = Intent(this, HomeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        
        // Finish this activity so it doesn't stay in the stack
        finish()
    }
}

