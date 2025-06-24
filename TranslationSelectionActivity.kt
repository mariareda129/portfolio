package com.example.arabicsignlanguage

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.arabicsignlanguage.databinding.ActivityTranslationSelectionBinding

class TranslationSelectionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTranslationSelectionBinding
    private val TAG = "TranslationSelectionActivity"
    
    // Permission launchers
    private val letterCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d(TAG, "Camera permission granted for LETTER mode")
            launchLetterTranslation()
        } else {
            Log.d(TAG, "Camera permission denied for LETTER mode")
        }
    }

    private val wordCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d(TAG, "Camera permission granted for WORD mode")
            launchWordTranslation()
        } else {
            Log.d(TAG, "Camera permission denied for WORD mode")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== TRANSLATION SELECTION ACTIVITY CREATED ===")
        
        binding = ActivityTranslationSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        startAnimations()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            Log.d(TAG, "Back button clicked - finishing TranslationSelectionActivity")
            finish()
        }
        
        binding.cardLetterTranslation.setOnClickListener {
            Log.d(TAG, "Letter translation card clicked")
            checkCameraPermissionAndStart("LETTER")
        }
        
        binding.cardWordTranslation.setOnClickListener {
            Log.d(TAG, "Word translation card clicked")
            checkCameraPermissionAndStart("WORD")
        }
    }
    
    private fun launchLetterTranslation() {
        Log.d(TAG, "=== LAUNCHING LETTER CAMERA ACTIVITY ===")
        val intent = Intent(this, LetterCameraActivity::class.java)
        startActivity(intent)
        Log.d(TAG, "LetterCameraActivity started")
    }
    
    private fun launchWordTranslation() {
        Log.d(TAG, "=== LAUNCHING WORD CAMERA ACTIVITY ===")
        val intent = Intent(this, WordCameraActivity::class.java)
        startActivity(intent)
        Log.d(TAG, "WordCameraActivity started")
    }
    
    private fun checkCameraPermissionAndStart(mode: String) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                Log.d(TAG, "Camera permission already granted, launching camera for mode: $mode")
                if (mode == "LETTER") {
                    launchLetterTranslation()
                } else {
                    launchWordTranslation()
                }
            }
            else -> {
                // Request permission
                Log.d(TAG, "Requesting camera permission for mode: $mode")
                if (mode == "LETTER") {
                    letterCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    wordCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }
    
    private fun startAnimations() {
        // Delay animations slightly
        Handler(Looper.getMainLooper()).postDelayed({
            // Fade in animation for title
            val titleAlpha = ObjectAnimator.ofFloat(binding.translationTitle, "alpha", 0f, 1f)
            val titleTranslateY = ObjectAnimator.ofFloat(binding.translationTitle, "translationY", -50f, 0f)
            
            // Fade in animations for content container
            val contentAlpha = ObjectAnimator.ofFloat(binding.contentContainer, "alpha", 0f, 1f)
            val contentTranslateY = ObjectAnimator.ofFloat(binding.contentContainer, "translationY", 50f, 0f)
            
            // Create animator sets
            val titleAnimator = AnimatorSet()
            titleAnimator.playTogether(titleAlpha, titleTranslateY)
            titleAnimator.duration = 800
            titleAnimator.interpolator = AccelerateDecelerateInterpolator()
            
            val contentAnimator = AnimatorSet()
            contentAnimator.playTogether(contentAlpha, contentTranslateY)
            contentAnimator.duration = 800
            contentAnimator.interpolator = AccelerateDecelerateInterpolator()
            
            // Start animations with slight delay
            titleAnimator.start()
            
            Handler(Looper.getMainLooper()).postDelayed({
                contentAnimator.start()
            }, 200)
            
        }, 100)
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== TRANSLATION SELECTION ACTIVITY RESUMED ===")
        Log.d(TAG, "User returned to TranslationSelectionActivity")
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "TranslationSelectionActivity onPause called")
    }
    
    override fun onStop() {
        super.onStop()
        Log.d(TAG, "TranslationSelectionActivity onStop called")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== TRANSLATION SELECTION ACTIVITY DESTROYED ===")
        Log.d(TAG, "WARNING: TranslationSelectionActivity is being destroyed!")
    }
    
    override fun onRestart() {
        super.onRestart()
        Log.d(TAG, "=== TRANSLATION SELECTION ACTIVITY RESTARTED ===")
        Log.d(TAG, "Returning from another activity")
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "=== TRANSLATION SELECTION ACTIVITY - NEW INTENT ===")
        Log.d(TAG, "Returned from camera activity")
    }
}
