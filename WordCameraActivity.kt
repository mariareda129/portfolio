package com.example.arabicsignlanguage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.arabicsignlanguage.databinding.ActivityWordCameraBinding
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WordCameraActivity : AppCompatActivity(), YoloDetector.DetectorListener {
    
    private lateinit var binding: ActivityWordCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var yoloDetector: YoloDetector
    private var isDetectorInitialized = false
    private var isFinishing = false
    private val TAG = "WordCameraActivity"
    
    // For approved functionality
    private var currentDetectedWord: String = ""
    private var currentConfidence: Float = 0f
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== WORD CAMERA ACTIVITY CREATED ===")
        
        binding = ActivityWordCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupDetector()
        setupCamera()
        
        // Handle back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Hardware back button pressed in WordCameraActivity")
                navigateBackToTranslationSelection()
            }
        })
    }
    
    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            Log.d(TAG, "Back button clicked in WordCameraActivity")
            navigateBackToTranslationSelection()
        }
        
        // Approved button
        binding.btnApproved.setOnClickListener {
            Log.d(TAG, "Approved button clicked")
            addWordToSentence()
        }
        
        // Clear button with options
        binding.btnClear.setOnClickListener {
            Log.d(TAG, "Clear button clicked")
            showClearOptions()
        }
        
        // Title
        binding.tvTitle.text = "ترجمة الكلمات"
        binding.tvSubtitle.text = "ضع يدك أمام الكاميرا لترجمة الكلمات"
        
        // Initialize overlay view
        binding.overlay.setWillNotDraw(false)
        // Remove setZOrderOnTop as it's not available for regular Views
        // The overlay is already positioned correctly in the layout
        
        // Disable keyboard for sentence EditText
        binding.etSentence.showSoftInputOnFocus = false
        binding.etSentence.isFocusable = true
        binding.etSentence.isFocusableInTouchMode = true
    }
    
    private fun setupDetector() {
        try {
            Log.d(TAG, "Setting up YOLO detector for WORD mode")
            // Use YOLO detector for words
            yoloDetector = YoloDetector(baseContext, Constants.MODEL_PATH, Constants.LABELS_PATH, this)
            yoloDetector.setup()
            isDetectorInitialized = true
            Log.d(TAG, "YOLO detector setup completed for WORD mode")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up detector: ${e.message}", e)
            isDetectorInitialized = false
            if (!isFinishing && !isDestroyed) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        try {
                            AlertDialog.Builder(this)
                                .setTitle("خطأ")
                                .setMessage("فشل في تحميل نموذج الذكاء الاصطناعي. يرجى المحاولة مرة أخرى.")
                                .setPositiveButton("موافق") { _, _ -> 
                                    navigateBackToTranslationSelection()
                                }
                                .setCancelable(false)
                                .show()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error showing detector error dialog: ${e.message}", e)
                            navigateBackToTranslationSelection()
                        }
                    }
                }
            }
        }
    }
    
    private fun setupCamera() {
        // Check camera permission
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            Log.e(TAG, "Camera permission not granted")
            showPermissionError()
        }
        
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                
                // Preview
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
                
                // Image capture
                imageCapture = ImageCapture.Builder().build()
                
                // Image analysis for detection
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(binding.viewFinder.display.rotation)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, { imageProxy ->
                            processImageForWords(imageProxy)
                        })
                    }
                
                // Select back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
                
                Log.d(TAG, "Word camera started successfully")
                
            } catch (exc: Exception) {
                Log.e(TAG, "Word camera startup failed", exc)
                showCameraError()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun processImageForWords(imageProxy: ImageProxy) {
        if (isFinishing || !isDetectorInitialized || imageProxy == null) {
            imageProxy?.close()
            return
        }
        
        try {
            // Create bitmap buffer from imageProxy
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            
            // Apply rotation matrix
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }
            
            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )
            
            // Run detection
            if (::yoloDetector.isInitialized && !isFinishing) {
                yoloDetector.detect(rotatedBitmap)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}", e)
        } finally {
            try {
                imageProxy.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing imageProxy: ${e.message}", e)
            }
        }
    }
    
    // Remove the old imageProxyToBitmap function as we're using the correct method now
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun showPermissionError() {
        AlertDialog.Builder(this)
            .setTitle("خطأ في الإذن")
            .setMessage("يجب منح إذن الكاميرا لاستخدام هذه الميزة")
            .setPositiveButton("موافق") { _, _ -> 
                navigateBackToTranslationSelection()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showCameraError() {
        AlertDialog.Builder(this)
            .setTitle("خطأ في الكاميرا")
            .setMessage("فشل في تشغيل الكاميرا. يرجى المحاولة مرة أخرى.")
            .setPositiveButton("موافق") { _, _ -> 
                navigateBackToTranslationSelection()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showDetectorError() {
        AlertDialog.Builder(this)
            .setTitle("خطأ")
            .setMessage("فشل في تحميل نموذج الذكاء الاصطناعي. يرجى المحاولة مرة أخرى.")
            .setPositiveButton("موافق") { _, _ -> 
                navigateBackToTranslationSelection()
            }
            .setCancelable(false)
            .show()
    }
    
    // YoloDetector.DetectorListener implementation
    override fun onEmptyDetect() {
        if (!isFinishing && !isDestroyed) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed && ::binding.isInitialized) {
                    try {
                        binding.tvDetectedWord.text = "الكلمة المكتشفة: -"
                        binding.tvConfidence.text = "دقة التعرف: -"
                        binding.overlay.clear()
                        
                        // Disable approved button when no detection
                        binding.btnApproved.isEnabled = false
                        currentDetectedWord = ""
                        currentConfidence = 0f
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onEmptyDetect UI update: ${e.message}", e)
                    }
                }
            }
        }
    }
    
    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        if (!isFinishing && !isDestroyed) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed && ::binding.isInitialized) {
                    try {
                        if (boundingBoxes.isNotEmpty()) {
                            val bestDetection = boundingBoxes.maxByOrNull { it.cnf }
                            bestDetection?.let { box ->
                                binding.tvDetectedWord.text = "الكلمة المكتشفة: ${box.clsName}"
                                binding.tvConfidence.text = "دقة التعرف: ${String.format("%.1f%%", box.cnf * 100)}"
                                
                                // Store current detection for approved button
                                currentDetectedWord = box.clsName
                                currentConfidence = box.cnf
                                
                                // Enable approved button when detection is good
                                binding.btnApproved.isEnabled = box.cnf > 0.5f // Enable if confidence > 50%
                            }
                            
                            // Draw bounding boxes
                            binding.overlay.apply {
                                setResults(boundingBoxes)
                                invalidate()
                            }
                        } else {
                            onEmptyDetect()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onDetect UI update: ${e.message}", e)
                    }
                }
            }
        }
    }
    
    private fun showClearOptions() {
        val currentText = binding.etSentence.text.toString()
        
        if (currentText.isEmpty()) {
            // Nothing to clear
            return
        }
        
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        
        // Option to clear last word
        if (currentText.trim().isNotEmpty()) {
            options.add("مسح آخر كلمة")
            actions.add { clearLastWord() }
        }
        
        // Option to clear all
        options.add("مسح الكل")
        actions.add { clearAll() }
        
        AlertDialog.Builder(this)
            .setTitle("خيارات المسح")
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
    
    private fun clearLastWord() {
        val currentText = binding.etSentence.text.toString().trim()
        if (currentText.isNotEmpty()) {
            val words = currentText.split(" ").toMutableList()
            if (words.isNotEmpty()) {
                words.removeLastOrNull()
                val newText = words.joinToString(" ")
                binding.etSentence.setText(newText)
                Log.d(TAG, "Cleared last word. New text: '$newText'")
            }
        }
    }
    
    private fun clearAll() {
        binding.etSentence.setText("")
        Log.d(TAG, "Cleared all text")
    }
    
    private fun addWordToSentence() {
        if (currentDetectedWord.isNotEmpty()) {
            val currentText = binding.etSentence.text.toString()
            val newText = if (currentText.isEmpty()) {
                currentDetectedWord
            } else {
                "$currentText $currentDetectedWord" // Add space between words
            }
            binding.etSentence.setText(newText)
            
            Log.d(TAG, "Added word '$currentDetectedWord' to sentence: '$newText'")
            
            // Optional: Show feedback
            binding.btnApproved.text = "تم ✓"
            binding.btnApproved.isEnabled = false
            
            // Reset button text after 1 second
            binding.btnApproved.postDelayed({
                binding.btnApproved.text = "موافق"
            }, 1000)
        }
    }
    
    private fun navigateBackToTranslationSelection() {
        Log.d(TAG, "=== NAVIGATING BACK TO TRANSLATION SELECTION FROM WORD CAMERA ===")
        
        isFinishing = true
        
        // إنشاء intent صريح للرجوع لـ TranslationSelectionActivity
        val intent = Intent(this, TranslationSelectionActivity::class.java).apply {
            // إزالة أي activities فوق TranslationSelectionActivity
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // استخدام نفس الـ instance إذا كان موجود
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // إضافة flag للتأكد من الرجوع الصحيح
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        
        Log.d(TAG, "Starting TranslationSelectionActivity with explicit intent")
        startActivity(intent)
        
        // إنهاء الـ WordCameraActivity
        finish()
        
        Log.d(TAG, "WordCameraActivity finished")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WordCameraActivity destroyed")
        
        isFinishing = true
        
        try {
            if (isDetectorInitialized && ::yoloDetector.isInitialized) {
                Log.d(TAG, "Clearing YOLO detector in onDestroy")
                yoloDetector.clear()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing detector: ${e.message}", e)
        }
        
        try {
            if (::cameraExecutor.isInitialized) {
                cameraExecutor.shutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down camera executor: ${e.message}", e)
        }
    }
    
    companion object {
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).toTypedArray()
    }
}
