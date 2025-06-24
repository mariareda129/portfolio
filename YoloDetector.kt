package com.example.arabicsignlanguage

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class YoloDetector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {

    private var interpreter: Interpreter? = null
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    fun setup() {
        android.util.Log.d("YoloDetector", "=== YOLO DETECTOR SETUP START ===")
        android.util.Log.d("YoloDetector", "Model path: $modelPath")
        android.util.Log.d("YoloDetector", "Labels path: $labelPath")
        
        try {
            val model = FileUtil.loadMappedFile(context, modelPath)
            val options = Interpreter.Options()
            options.numThreads = 4
            interpreter = Interpreter(model, options)
            
            android.util.Log.d("YoloDetector", "Model loaded successfully")

            val inputShape = interpreter?.getInputTensor(0)?.shape() ?: return
            val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return
            
            android.util.Log.d("YoloDetector", "Input shape: ${inputShape.contentToString()}")
            android.util.Log.d("YoloDetector", "Output shape: ${outputShape.contentToString()}")

            tensorWidth = inputShape[1]
            tensorHeight = inputShape[2]
            numChannel = outputShape[1]
            numElements = outputShape[2]
            
            android.util.Log.d("YoloDetector", "Tensor dimensions - Width: $tensorWidth, Height: $tensorHeight, Channels: $numChannel, Elements: $numElements")

            // Load labels
            loadLabels()
            
            android.util.Log.d("YoloDetector", "=== YOLO DETECTOR SETUP COMPLETE ===")
        } catch (e: Exception) {
            android.util.Log.e("YoloDetector", "Setup failed: ${e.message}", e)
            throw e
        }
    }

    private fun loadLabels() {
        try {
            val inputStream: InputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String? = reader.readLine()
            while (line != null && line.trim().isNotEmpty()) {
                labels.add(line.trim())
                android.util.Log.d("YoloDetector", "Loaded WORD label: ${line.trim()}")
                line = reader.readLine()
            }

            reader.close()
            inputStream.close()
            android.util.Log.d("YoloDetector", "Total WORD labels loaded: ${labels.size}")
        } catch (e: IOException) {
            android.util.Log.e("YoloDetector", "Error loading labels: ${e.message}")
            throw e
        }
    }

    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    fun detect(frame: Bitmap) {
        interpreter ?: return
        if (tensorWidth == 0) return
        if (tensorHeight == 0) return
        if (numChannel == 0) return
        if (numElements == 0) return

        var inferenceTime = SystemClock.uptimeMillis()

        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)
        android.util.Log.d("YoloDetector", "Processing frame ${frame.width}x${frame.height} -> ${tensorWidth}x${tensorHeight}")

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val imageBuffer = processedImage.buffer

        val output = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter?.run(imageBuffer, output.buffer)
        
        android.util.Log.d("YoloDetector", "YOLO inference completed, processing results...")

        val bestBoxes = bestBox(output.floatArray)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        
        android.util.Log.d("YoloDetector", "YOLO found ${bestBoxes?.size ?: 0} WORD detections in ${inferenceTime}ms")

        if (bestBoxes == null || bestBoxes.isEmpty()) {
            detectorListener.onEmptyDetect()
            return
        }

        detectorListener.onDetect(bestBoxes, inferenceTime)
    }

    private fun bestBox(array: FloatArray): List<BoundingBox>? {
        android.util.Log.d("YoloDetector", "YOLO processing ${array.size} output values")
        android.util.Log.d("YoloDetector", "YOLO numElements: $numElements, numChannel: $numChannel")

        val boundingBoxes = mutableListOf<BoundingBox>()

        for (c in 0 until numElements) {
            var maxConf = -1.0f
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j
            
            while (j < numChannel) {
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                android.util.Log.d("YoloDetector", "YOLO detection: confidence=$maxConf, class=$maxIdx")
                
                if (maxIdx >= 0 && maxIdx < labels.size) {
                    val clsName = labels[maxIdx]
                    val cx = array[c] // center x
                    val cy = array[c + numElements] // center y
                    val w = array[c + numElements * 2] // width
                    val h = array[c + numElements * 3] // height
                    val x1 = cx - (w / 2F)
                    val y1 = cy - (h / 2F)
                    val x2 = cx + (w / 2F)
                    val y2 = cy + (h / 2F)
                    
                    android.util.Log.d("YoloDetector", "YOLO box: cx=$cx, cy=$cy, w=$w, h=$h -> x1=$x1, y1=$y1, x2=$x2, y2=$y2")
                    
                    // Validate coordinates are in normalized range [0,1]
                    if (x1 >= 0F && x1 <= 1F && y1 >= 0F && y1 <= 1F && 
                        x2 >= 0F && x2 <= 1F && y2 >= 0F && y2 <= 1F && 
                        x2 > x1 && y2 > y1) {
                        
                        boundingBoxes.add(
                            BoundingBox(
                                x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                                cx = cx, cy = cy, w = w, h = h,
                                cnf = maxConf, cls = maxIdx, clsName = clsName
                            )
                        )
                        
                        android.util.Log.d("YoloDetector", "Added YOLO WORD detection: $clsName (${(maxConf * 100).toInt()}%)")
                    } else {
                        android.util.Log.d("YoloDetector", "YOLO box coordinates out of bounds, skipping")
                    }
                }
            }
        }

        android.util.Log.d("YoloDetector", "YOLO boxes before NMS: ${boundingBoxes.size}")

        if (boundingBoxes.isEmpty()) return null

        val finalBoxes = applyNMS(boundingBoxes)
        android.util.Log.d("YoloDetector", "YOLO final WORD boxes after NMS: ${finalBoxes.size}")
        
        return finalBoxes
    }

    private fun applyNMS(boxes: List<BoundingBox>): MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.15F // Very low threshold for testing
        private const val IOU_THRESHOLD = 0.5F
    }
}
