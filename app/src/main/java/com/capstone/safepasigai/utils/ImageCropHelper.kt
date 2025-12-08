package com.capstone.safepasigai.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream

/**
 * Helper class for image picking and cropping to 1:1 ratio.
 */
object ImageCropHelper {
    
    /**
     * Create UCrop intent for 1:1 cropping.
     */
    fun createCropIntent(context: Context, sourceUri: Uri): Intent {
        val destinationFileName = "cropped_${System.currentTimeMillis()}.jpg"
        val destinationUri = Uri.fromFile(File(context.cacheDir, destinationFileName))
        
        return UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)  // 1:1 ratio
            .withMaxResultSize(512, 512)  // Max size
            .withOptions(getUCropOptions())
            .getIntent(context)
    }
    
    private fun getUCropOptions(): UCrop.Options {
        return UCrop.Options().apply {
            setCompressionQuality(85)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)  // Force 1:1 ratio
            setShowCropGrid(true)
            setShowCropFrame(true)
            setCircleDimmedLayer(true)  // Show circular crop preview
            setToolbarTitle("Crop Photo")
        }
    }
    
    /**
     * Handle UCrop result and return the cropped image path.
     */
    fun handleCropResult(resultCode: Int, data: Intent?): String? {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val resultUri = UCrop.getOutput(data)
            return resultUri?.path
        }
        return null
    }
    
    /**
     * Save cropped image to app's internal storage.
     */
    fun saveCroppedImage(context: Context, croppedPath: String, prefix: String = "avatar"): String? {
        return try {
            val sourceFile = File(croppedPath)
            if (!sourceFile.exists()) return null
            
            val fileName = "${prefix}_${System.currentTimeMillis()}.jpg"
            val destFile = File(context.filesDir, fileName)
            
            sourceFile.inputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Clean up cache file
            sourceFile.delete()
            
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get a content URI for a file (needed for some operations).
     */
    fun getContentUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
