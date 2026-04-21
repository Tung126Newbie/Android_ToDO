package com.example.simplenotes.presentation.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.simplenotes.presentation.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmService.ACTION_STOP_ALARM) {
            val stopIntent = Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
            }
            context.stopService(stopIntent)
            return
        }

        val noteId = intent.getLongExtra("note_id", 0L)
        val title = intent.getStringExtra("title") ?: "Ghi chú"

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("note_id", noteId)
            putExtra("title", title)
        }
        
        context.startForegroundService(serviceIntent)
    }
}
