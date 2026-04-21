// presentation/worker/NotificationWorker.kt
package com.example.simplenotes.presentation.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.simplenotes.presentation.MainActivity

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val noteId = inputData.getLong("note_id", 0)
        val title = inputData.getString("title") ?: "Ghi chú"

        showNotification(noteId, title)
        return Result.success()
    }

    private fun showNotification(noteId: Long, title: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "reminder_channel",
                "Nhắc nhở ghi chú",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi đến thời gian hẹn"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("note_id", noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "reminder_channel")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Nhắc nhở: $title")
            .setContentText("Đã đến thời gian ghi chú này")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(noteId.toInt(), notification)
    }
}