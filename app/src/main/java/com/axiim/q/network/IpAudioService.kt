package com.axiim.q.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.axiim.q.MainActivity
import com.axiim.q.R

/**
 * Foreground service that keeps "Listen on Phone" alive while the app is in the background or the
 * screen is off. Without it Android freezes the process, the CPU sleeps and Wi-Fi power save
 * drops the RTP packets, so playback stops shortly after leaving the app.
 *
 * The service holds no audio itself: [IpAudioReceiver] does the work, this only keeps the process
 * at foreground priority and holds a CPU wake lock plus a Wi-Fi lock for the stream's lifetime.
 */
class IpAudioService : Service() {
  companion object {
    private const val CHANNEL_ID = "private_listen"
    private const val NOTIFICATION_ID = 1001
    private const val ACTION_STOP = "com.axiim.q.action.STOP_PRIVATE_LISTEN"

    /** Called when the user taps "Stop" on the notification; set by the repository. */
    @Volatile var onStopRequested: (() -> Unit)? = null

    fun start(context: Context) {
      runCatching { ContextCompat.startForegroundService(context, Intent(context, IpAudioService::class.java)) }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, IpAudioService::class.java))
    }
  }

  private var wakeLock: PowerManager.WakeLock? = null
  private val wifiLocks = mutableListOf<WifiManager.WifiLock>()

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      onStopRequested?.invoke()
      stopSelf()
      return START_NOT_STICKY
    }
    val type =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
    ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    acquireLocks()
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    releaseLocks()
    super.onDestroy()
  }

  private fun acquireLocks() {
    if (wakeLock == null) {
      val power = getSystemService(POWER_SERVICE) as PowerManager
      wakeLock =
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AxiimQ:PrivateListen").apply {
          setReferenceCounted(false)
          acquire()
        }
    }
    if (wifiLocks.isEmpty()) {
      val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
      @Suppress("DEPRECATION")
      wifiLocks += wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AxiimQ:PrivateListen")
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        wifiLocks += wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "AxiimQ:PrivateListenLowLatency")
      }
      wifiLocks.forEach {
        it.setReferenceCounted(false)
        runCatching { it.acquire() }
      }
    }
  }

  private fun releaseLocks() {
    runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    wakeLock = null
    wifiLocks.forEach { lock -> runCatching { if (lock.isHeld) lock.release() } }
    wifiLocks.clear()
  }

  private fun buildNotification(): Notification {
    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Private Listen", NotificationManager.IMPORTANCE_LOW).apply {
          description = "Shown while Axiim Q audio is streaming to this phone"
          setShowBadge(false)
        },
      )
    }
    val openApp =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val stop =
      PendingIntent.getService(
        this,
        1,
        Intent(this, IpAudioService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_monochrome)
      .setContentTitle("Private Listen")
      .setContentText("Streaming Axiim Q audio to this phone")
      .setContentIntent(openApp)
      .addAction(0, "Stop", stop)
      .setOngoing(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .build()
  }
}
