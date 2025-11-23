package com.blue.glassesapp.feature.recognition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.blue.glassesapp.core.utils.CxrUtil
import com.google.gson.Gson
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.utils.ValueUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 照片识别服务
 * 接收来自眼镜端的照片，进行AI识别，返回结果
 */
class PhotoRecognitionService {
    
    private val TAG = "PhotoRecognitionService"
    private val gson = Gson()
    
    /**
     * 消息监听回调
     */
    interface MessageListener {
        fun onPhotoReceived(bitmap: Bitmap)
        fun onRecognitionComplete(result: RecognitionResult)
    }
    
    private var messageListener: MessageListener? = null
    
    fun setMessageListener(listener: MessageListener) {
        this.messageListener = listener
    }
    
    /**
     * 初始化消息监听
     * 监听来自眼镜端的消息
     */
    fun initMessageListener() {
        Log.d(TAG, "初始化照片识别服务消息监听")
        
        // TODO: 根据实际Rokid SDK API实现消息接收
        // CxrApi.getInstance().setCustomMessageListener { message ->
        //     handleGlassesMessage(message)
        // }
        
        // 临时方案：使用已有的消息监听机制
        Log.d(TAG, "等待接收眼镜端照片...")
    }
    
    /**
     * 处理来自眼镜端的消息
     */
    fun handleGlassesMessage(jsonMessage: String) {
        try {
            Log.d(TAG, "收到眼镜端消息")
            
            // 解析消息
            val photoMessage = gson.fromJson(jsonMessage, PhotoMessage::class.java)
            
            if (photoMessage.type == "photo_recognition") {
                Log.d(TAG, "收到拍照识别请求")
                
                // Base64解码为Bitmap
                val bitmap = base64ToBitmap(photoMessage.imageData)
                
                // 通知接收到照片
                messageListener?.onPhotoReceived(bitmap)
                
                // 执行识别
                performRecognition(bitmap)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理消息失败: ${e.message}", e)
            sendErrorToGlasses("消息解析失败: ${e.message}")
        }
    }
    
    /**
     * 执行照片识别
     */
    private suspend fun performRecognition(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始识别照片: ${bitmap.width}x${bitmap.height}")
            
            // 模拟识别处理
            // TODO: 接入真实的AI识别服务（如百度OCR、腾讯云OCR、本地模型等）
            val result = mockRecognition(bitmap)
            
            Log.d(TAG, "识别完成: ${result.content}")
            
            // 通知识别完成
            withContext(Dispatchers.Main) {
                messageListener?.onRecognitionComplete(result)
            }
            
            // 发送结果到眼镜端
            sendResultToGlasses(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "识别失败: ${e.message}", e)
            sendErrorToGlasses("识别失败: ${e.message}")
        }
    }
    
    /**
     * 模拟识别（实际项目中替换为真实AI服务）
     */
    private fun mockRecognition(bitmap: Bitmap): RecognitionResult {
        // 模拟识别延迟
        Thread.sleep(2000)
        
        // 简单的图像分析
        val brightness = analyzeBrightness(bitmap)
        val colorInfo = analyzeColor(bitmap)
        
        val content = buildString {
            append("图像分析结果：\n")
            append("尺寸：${bitmap.width}x${bitmap.height}\n")
            append("亮度：$brightness\n")
            append("主色调：$colorInfo\n")
            append("\n这是一个模拟识别结果。\n")
            append("实际应用中，请接入真实的AI识别服务。")
        }
        
        return RecognitionResult(
            status = "success",
            content = content,
            confidence = 0.95f,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 分析图像亮度
     */
    private fun analyzeBrightness(bitmap: Bitmap): String {
        var totalBrightness = 0L
        val sampleSize = 10
        val width = bitmap.width
        val height = bitmap.height
        
        for (x in 0 until width step sampleSize) {
            for (y in 0 until height step sampleSize) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                totalBrightness += (r + g + b) / 3
            }
        }
        
        val avgBrightness = totalBrightness / ((width / sampleSize) * (height / sampleSize))
        
        return when {
            avgBrightness > 180 -> "明亮"
            avgBrightness > 100 -> "适中"
            else -> "较暗"
        }
    }
    
    /**
     * 分析主色调
     */
    private fun analyzeColor(bitmap: Bitmap): String {
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val r = (pixel shr 16) and 0xff
        val g = (pixel shr 8) and 0xff
        val b = pixel and 0xff
        
        return when {
            r > g && r > b -> "偏红色"
            g > r && g > b -> "偏绿色"
            b > r && b > g -> "偏蓝色"
            r == g && g == b -> "灰色"
            else -> "混合色"
        }
    }
    
    /**
     * 发送识别结果到眼镜端
     */
    private fun sendResultToGlasses(result: RecognitionResult) {
        try {
            val jsonResult = gson.toJson(result)
            
            val status = CxrApi.getInstance().sendGlobalMsgContent(
                1, // 消息类型
                jsonResult,
                false
            )
            
            when (status) {
                ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                    Log.d(TAG, "识别结果已发送到眼镜端")
                }
                else -> {
                    Log.e(TAG, "发送结果失败: $status")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "发送结果异常: ${e.message}", e)
        }
    }
    
    /**
     * 发送错误信息到眼镜端
     */
    private fun sendErrorToGlasses(errorMsg: String) {
        val errorResult = RecognitionResult(
            status = "error",
            content = errorMsg,
            confidence = 0f,
            timestamp = System.currentTimeMillis()
        )
        sendResultToGlasses(errorResult)
    }
    
    /**
     * Base64转Bitmap
     */
    private fun base64ToBitmap(base64String: String): Bitmap {
        val decodedBytes = Base64.decode(base64String, Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }
    
    /**
     * 照片消息数据类
     */
    data class PhotoMessage(
        val type: String,
        val imageData: String,
        val timestamp: Long
    )
    
    /**
     * 识别结果数据类
     */
    data class RecognitionResult(
        val status: String,      // success / error / processing
        val content: String,     // 识别结果内容
        val confidence: Float,   // 置信度
        val timestamp: Long
    )
}
