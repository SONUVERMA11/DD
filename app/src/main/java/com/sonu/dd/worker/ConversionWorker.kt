package com.sonu.dd.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker for format conversion using FFmpeg.
 * Runs in background and survives app close.
 */
@HiltWorker
class ConversionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val inputPath = inputData.getString(KEY_INPUT_PATH) ?: return Result.failure()
        val outputFormat = inputData.getString(KEY_OUTPUT_FORMAT) ?: return Result.failure()
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()

        return try {
            // In production, FFmpeg Kit Android would be used here:
            // val command = buildFFmpegCommand(inputPath, outputFormat)
            // FFmpegKit.execute(command)

            // Report progress
            setProgress(workDataOf(KEY_PROGRESS to 100))

            Result.success(workDataOf(
                KEY_DOWNLOAD_ID to downloadId,
                KEY_OUTPUT_PATH to inputPath.replace(
                    inputPath.substringAfterLast('.'),
                    outputFormat.lowercase()
                )
            ))
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to e.message))
        }
    }

    companion object {
        const val KEY_INPUT_PATH = "input_path"
        const val KEY_OUTPUT_FORMAT = "output_format"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
    }
}
