package com.example.arabicsignlanguage

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {

    private var interpreter: Interpreter? = null
    private var labels = mutableListOf<String>()

    // TensorFlow Object Detection API model configuration
    private val inputSize = 300
    private val numDetections = 10
    private val imageMean = 128.0f
    private val imageStd = 128.0f
    private val numThreads = 4
    private val confidenceThreshold = 0.2f // Lower threshold for testing

    // Pre-allocated buffers
    private var intValues: IntArray? = null
    private var imgData: ByteBuffer? = null
    
    // Output arrays - TensorFlow Object Detection API format
    private var outputLocations: Array<Array<FloatArray>>? = null  // [1][10][4]
    private var outputClasses: Array<FloatArray>? = null          // [1][10]
    private var outputScores: Array<FloatArray>? = null           // [1][10]
    private var numDetectionsArray: FloatArray? = null            // [1]

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    fun setup() {
        android.util.Log.d("TFDetector", "=== TENSORFLOW DETECTOR SETUP START ===")
        android.util.Log.d("TFDetector", "Model path: $modelPath")
        android.util.Log.d("TFDetector", "Labels path: $labelPath")
        
        try {
            // Load model
            val model = FileUtil.loadMappedFile(context, modelPath)
            val options = Interpreter.Options()
            options.numThreads = numThreads
            interpreter = Interpreter(model, options)
            android.util.Log.d("TFDetector", "TensorFlow model loaded successfully")

            // Get model input/output info
            val inputTensor = interpreter?.getInputTensor(0)
            android.util.Log.d("TFDetector", "Input tensor shape: ${inputTensor?.shape()?.contentToString()}")
            android.util.Log.d("TFDetector", "Input tensor type: ${inputTensor?.dataType()}")
            android.util.Log.d("TFDetector", "Number of input tensors: ${interpreter?.inputTensorCount}")
            android.util.Log.d("TFDetector", "Number of output tensors: ${interpreter?.outputTensorCount}")

            // Log all output tensor shapes
            for (i in 0 until (interpreter?.outputTensorCount ?: 0)) {
                val outputTensor = interpreter?.getOutputTensor(i)
                android.util.Log.d("TFDetector", "Output tensor $i shape: ${outputTensor?.shape()?.contentToString()}")
                android.util.Log.d("TFDetector", "Output tensor $i type: ${outputTensor?.dataType()}")
            }

            // Load labels
            loadLabels()

            // Pre-allocate buffers
            val numBytesPerChannel = 4 // Float32
            imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * numBytesPerChannel)
            imgData?.order(ByteOrder.nativeOrder())
            intValues = IntArray(inputSize * inputSize)

            // Initialize output arrays for TensorFlow Object Detection API
            outputLocations = Array(1) { Array(numDetections) { FloatArray(4) } }
            outputClasses = Array(1) { FloatArray(numDetections) }
            outputScores = Array(1) { FloatArray(numDetections) }
            numDetectionsArray = FloatArray(1)

            android.util.Log.d("TFDetector", "=== TENSORFLOW DETECTOR SETUP COMPLETE ===")

        } catch (e: Exception) {
            android.util.Log.e("TFDetector", "Setup failed: ${e.message}", e)
            throw e
        }
    }

    private fun loadLabels() {
        try {
            val inputStream: InputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String? = reader.readLine()
            while (line != null) {
                if (line.trim().isNotEmpty()) {
                    labels.add(line.trim())
                    android.util.Log.d("TFDetector", "Loaded LETTER label: ${line.trim()}")
                }
                line = reader.readLine()
            }

            reader.close()
            inputStream.close()
            android.util.Log.d("TFDetector", "Total LETTER labels loaded: ${labels.size}")
        } catch (e: IOException) {
            android.util.Log.e("TFDetector", "Error loading labels: ${e.message}")
            throw e
        }
    }

    fun detect(frame: Bitmap) {
        try {
            interpreter ?: return
            if (intValues == null || imgData == null) return

            val inferenceStartTime = SystemClock.uptimeMillis()

            // Resize bitmap to model input size
            val resizedBitmap = Bitmap.createScaledBitmap(frame, inputSize, inputSize, false)
            android.util.Log.d("TFDetector", "Processing frame ${frame.width}x${frame.height} -> ${inputSize}x${inputSize}")

            // Preprocess image
            preprocessImage(resizedBitmap)

            // Run inference
            runInference()

            // Process results
            val detections = processResults()
            
            val inferenceTime = SystemClock.uptimeMillis() - inferenceStartTime
            android.util.Log.d("TFDetector", "TensorFlow found ${detections.size} LETTER detections in ${inferenceTime}ms")

            if (detections.isEmpty()) {
                detectorListener.onEmptyDetect()
            } else {
                detectorListener.onDetect(detections, inferenceTime)
            }

        } catch (e: Exception) {
            android.util.Log.e("TFDetector", "Error in detect: ${e.message}", e)
            detectorListener.onEmptyDetect()
        }
    }

    private fun preprocessImage(bitmap: Bitmap) {
        // Get pixels from bitmap
        intValues?.let { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }

        imgData?.rewind()
        
        // Convert pixels to float and normalize for TensorFlow Object Detection API
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues!![i * inputSize + j]
                
                // Extract RGB values
                val r = ((pixelValue shr 16) and 0xFF)
                val g = ((pixelValue shr 8) and 0xFF)
                val b = (pixelValue and 0xFF)
                
                // TensorFlow Object Detection API normalization: (pixel - 128.0) / 128.0
                imgData?.putFloat((r - imageMean) / imageStd)
                imgData?.putFloat((g - imageMean) / imageStd)
                imgData?.putFloat((b - imageMean) / imageStd)
            }
        }
    }

    private fun runInference() {
        try {
            // Prepare input and output maps for TensorFlow Object Detection API
            val inputArray = arrayOf(imgData)
            val outputMap = HashMap<Int, Any>()
            
            outputMap[0] = outputLocations!!
            outputMap[1] = outputClasses!!
            outputMap[2] = outputScores!!
            outputMap[3] = numDetectionsArray!!

            android.util.Log.d("TFDetector", "Running TensorFlow inference...")

            // Run inference
            interpreter?.runForMultipleInputsOutputs(inputArray, outputMap)
            
            android.util.Log.d("TFDetector", "TensorFlow inference completed")
            android.util.Log.d("TFDetector", "Number of detections: ${numDetectionsArray!![0]}")
            
        } catch (e: Exception) {
            android.util.Log.e("TFDetector", "Error during inference: ${e.message}", e)
            throw e
        }
    }

    private fun processResults(): List<BoundingBox> {
        val detections = mutableListOf<BoundingBox>()

        for (i in 0 until numDetections) {
            val score = outputScores!![0][i]
            
            android.util.Log.d("TFDetector", "Detection $i: score=$score, threshold=$confidenceThreshold")
            
            // Filter by confidence threshold
            if (score >= confidenceThreshold) {
                // Get bounding box coordinates - TensorFlow format: [y1, x1, y2, x2] (normalized)
                val y1 = outputLocations!![0][i][0]
                val x1 = outputLocations!![0][i][1]
                val y2 = outputLocations!![0][i][2]
                val x2 = outputLocations!![0][i][3]

                // Get class index (no offset needed for this model)
                val classIndex = outputClasses!![0][i].toInt()
                
                android.util.Log.d("TFDetector", "TensorFlow detection: class=$classIndex, coords=[$y1, $x1, $y2, $x2]")
                
                // Validate class index and coordinates
                if (classIndex >= 0 && classIndex < labels.size && 
                    x1 >= 0f && x1 <= 1f && y1 >= 0f && y1 <= 1f &&
                    x2 >= 0f && x2 <= 1f && y2 >= 0f && y2 <= 1f &&
                    x2 > x1 && y2 > y1) {
                    
                    val className = labels[classIndex]
                    
                    // Create BoundingBox object with normalized coordinates
                    val boundingBox = BoundingBox(
                        x1 = x1,
                        y1 = y1,
                        x2 = x2,
                        y2 = y2,
                        cx = (x1 + x2) / 2f,
                        cy = (y1 + y2) / 2f,
                        w = x2 - x1,
                        h = y2 - y1,
                        cnf = score,
                        cls = classIndex,
                        clsName = className
                    )
                    
                    detections.add(boundingBox)
                    android.util.Log.d("TFDetector", "Added TensorFlow LETTER detection: $className (${(score * 100).toInt()}%)")
                } else {
                    android.util.Log.d("TFDetector", "Invalid detection: classIndex=$classIndex, coords=[$x1,$y1,$x2,$y2]")
                }
            }
        }

        android.util.Log.d("TFDetector", "TensorFlow final LETTER detections: ${detections.size}")
        return detections
    }

    fun clear() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            android.util.Log.e("TFDetector", "Error clearing detector: ${e.message}")
        }
    }
}
