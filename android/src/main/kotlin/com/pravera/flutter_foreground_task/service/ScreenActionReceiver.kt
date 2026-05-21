package com.pravera.flutter_foreground_task.service

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.pravera.flutter_foreground_task.models.ForegroundServiceAction
import com.pravera.flutter_foreground_task.models.ForegroundServiceStatus
import com.pravera.flutter_foreground_task.utils.PluginUtils

/**
 * Receiver for screen/user unlock events.
 */
class ScreenActionReceiver : BroadcastReceiver() {

    companion object {
        private val TAG = ScreenActionReceiver::class.java.simpleName

        private var receiver: ScreenActionReceiver? = null

        fun register(context: Context) {

            if (receiver != null) {
                Log.d(TAG, "Receiver already registered")
                return
            }

            receiver = ScreenActionReceiver()

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(receiver, filter)
            }

            Log.d(TAG, "ScreenActionReceiver registered")
        }

        fun unregister(context: Context) {

            try {

                receiver?.let {
                    context.unregisterReceiver(it)
                    Log.d(TAG, "ScreenActionReceiver unregistered")
                }

            } catch (e: Exception) {

                Log.e(TAG, e.message, e)

            } finally {

                receiver = null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context?, intent: Intent?) {

        if (context == null || intent == null) return

        try {

            val action = intent.action ?: return

            when (action) {

                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {

                    Log.d(TAG, "Received: $action")

                    val serviceStatus =
                        ForegroundServiceStatus.getData(context)

                    if (serviceStatus.isCorrectlyStopped()) {
                        Log.d(TAG, "Service correctly stopped.")
                        return
                    }

                    val manager =
                        context.getSystemService(Context.ACTIVITY_SERVICE)
                                as ActivityManager

                    val isRunningService =
                        manager.getRunningServices(Integer.MAX_VALUE)
                            .any {
                                it.service.className ==
                                        ForegroundService::class.java.name
                            }

                    if (isRunningService) {
                        Log.d(TAG, "Service already running")
                        return
                    }

                    val isIgnoringBatteryOptimizations =
                        PluginUtils.isIgnoringBatteryOptimizations(context)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        !isIgnoringBatteryOptimizations
                    ) {
                        Log.w(
                            TAG,
                            "Battery optimization may block restart"
                        )
                    }

                    try {

                        val nIntent =
                            Intent(context, ForegroundService::class.java)

                        ForegroundServiceStatus.setData(
                            context,
                            ForegroundServiceAction.RESTART
                        )

                        ContextCompat.startForegroundService(
                            context,
                            nIntent
                        )

                        Log.d(TAG, "Foreground service restarted")

                    } catch (e: ForegroundServiceStartNotAllowedException) {

                        Log.e(
                            TAG,
                            "FGS start not allowed: ${e.message}"
                        )

                    } catch (e: Exception) {

                        Log.e(TAG, e.message, e)
                    }
                }
            }

        } catch (e: Exception) {

            Log.e(TAG, e.message, e)
        }
    }
}