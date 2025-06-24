package com.example.arabicsignlanguage

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.arabicsignlanguage.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityHomeBinding
    private val TAG = "HomeActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== HOME ACTIVITY CREATED ===")
        
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        startAnimations()
    }
    
    private fun setupClickListeners() {
        binding.btnTranslation.setOnClickListener {
            Log.d(TAG, "Translation button clicked - navigating to TranslationSelectionActivity")
            val intent = Intent(this, TranslationSelectionActivity::class.java)
            startActivity(intent)
        }
        
        binding.btnDictionary.setOnClickListener {
            Log.d(TAG, "Dictionary button clicked - navigating to DictionaryActivity")
            val intent = Intent(this, DictionaryActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun startAnimations() {
        // Delay animations slightly
        Handler(Looper.getMainLooper()).postDelayed({
            // Fade in animations for title
            val titleAlpha = ObjectAnimator.ofFloat(binding.homeTitle, "alpha", 0f, 1f)
            val titleTranslateY = ObjectAnimator.ofFloat(binding.homeTitle, "translationY", -50f, 0f)
            
            // Fade in animation for image
            val imageAlpha = ObjectAnimator.ofFloat(binding.homeImage, "alpha", 0f, 1f)
            val imageScale = ObjectAnimator.ofFloat(binding.homeImage, "scaleX", 0.8f, 1f)
            val imageScaleY = ObjectAnimator.ofFloat(binding.homeImage, "scaleY", 0.8f, 1f)
            
            // Fade in animations for buttons
            val buttonsAlpha = ObjectAnimator.ofFloat(binding.buttonsContainer, "alpha", 0f, 1f)
            val buttonsTranslateY = ObjectAnimator.ofFloat(binding.buttonsContainer, "translationY", 50f, 0f)
            
            // Create animator sets
            val titleAnimator = AnimatorSet()
            titleAnimator.playTogether(titleAlpha, titleTranslateY)
            titleAnimator.duration = 800
            titleAnimator.interpolator = AccelerateDecelerateInterpolator()
            
            val imageAnimator = AnimatorSet()
            imageAnimator.playTogether(imageAlpha, imageScale, imageScaleY)
            imageAnimator.duration = 800
            imageAnimator.interpolator = AccelerateDecelerateInterpolator()
            
            val buttonsAnimator = AnimatorSet()
            buttonsAnimator.playTogether(buttonsAlpha, buttonsTranslateY)
            buttonsAnimator.duration = 800
            buttonsAnimator.interpolator = AccelerateDecelerateInterpolator()
            
            // Start animations with slight delays
            titleAnimator.start()
            
            Handler(Looper.getMainLooper()).postDelayed({
                imageAnimator.start()
            }, 200)
            
            Handler(Looper.getMainLooper()).postDelayed({
                buttonsAnimator.start()
            }, 400)
            
        }, 100)
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== HOME ACTIVITY RESUMED ===")
    }
}
