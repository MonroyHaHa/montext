package com.monroy.montext.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.monroy.montext.R
import com.monroy.montext.data.network.XmppConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest // 导入 collectLatest

/**
 * Android 服务，用于在后台维护 XMPP 连接。
 * 它将作为一个前台服务运行，以提高进程优先级。
 */
class XmppConnectionService : Service() {

    private val TAG = "XmppConnectionService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // 用于协程的独立作用域

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "XMPP_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 101

        // Intent Actions
        const val ACTION_CONNECT = "com.monroy.montext.services.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.monroy.montext.services.ACTION_DISCONNECT"
        const val ACTION_LOGIN = "com.monroy.montext.services.ACTION_LOGIN"
        const val ACTION_REGISTER = "com.monroy.montext.services.ACTION_REGISTER"
        // TODO: 后续添加发送消息等动作
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "XmppConnectionService onCreate")
        createNotificationChannel() // 创建通知渠道
        startForeground(NOTIFICATION_ID, createNotification()) // 启动为前台服务
        observeConnectionAndLoginState() // 观察连接和登录状态
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "XmppConnectionService onStartCommand")

        val action = intent?.getStringExtra("action")
        val username = intent?.getStringExtra("username")
        val password = intent?.getStringExtra("password")
        val serverIp = intent?.getStringExtra("server_ip")
        val serverPort = intent?.getIntExtra("server_port", 5222)
        val serverDomain = intent?.getStringExtra("server_domain")

        val realName = intent?.getStringExtra("real_name") ?: ""
        val email = intent?.getStringExtra("email") ?: ""

        when (action) {
            ACTION_LOGIN -> {
                if (!username.isNullOrEmpty() && !password.isNullOrEmpty() &&
                    !serverIp.isNullOrEmpty() && !serverDomain.isNullOrEmpty() && serverPort != null
                ) {
                    serviceScope.launch {
                        // 先初始化连接（如果未连接）
                        XmppConnectionManager.initializeAndConnect(serverIp, serverPort, serverDomain)
                        // 等待连接成功，再尝试登录
                        XmppConnectionManager.connectionState.collectLatest { state ->
                            if (state == XmppConnectionManager.ConnectionState.CONNECTED) {
                                Log.d(TAG, "Connection successful, attempting login...")
                                XmppConnectionManager.login(username, password)
                                // 只处理一次连接成功的事件，然后取消 collectLatest
                                throw kotlinx.coroutines.CancellationException("Connection handled")
                            } else if (state == XmppConnectionManager.ConnectionState.DISCONNECTED) {
                                // 如果连接直接失败，也需要通知UI
                                Log.e(TAG, "Connection failed during login attempt.")
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Login action requires username, password, IP, port, and domain.")
                }
            }
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    XmppConnectionManager.disconnect()
                }
            }
            ACTION_REGISTER -> {
                if (!username.isNullOrEmpty() && !password.isNullOrEmpty() &&
                    !serverIp.isNullOrEmpty() && !serverDomain.isNullOrEmpty() && serverPort != null
                ) {
                    serviceScope.launch {
                        XmppConnectionManager.initializeAndConnect(serverIp, serverPort, serverDomain)
                        XmppConnectionManager.connectionState.collectLatest { state ->
                            if (state == XmppConnectionManager.ConnectionState.CONNECTED) {
                                Log.d(TAG, "Connection successful, attempting registration...")
                                XmppConnectionManager.register(username, password,realName,email)
                                throw kotlinx.coroutines.CancellationException("Connection handled for registration")
                            } else if (state == XmppConnectionManager.ConnectionState.DISCONNECTED) {
                                Log.e(TAG, "Connection failed during registration attempt.")
                                // ====== 修正点：调用公共方法更新状态 ======
                                XmppConnectionManager.updateRegistrationState(XmppConnectionManager.RegistrationState.REGISTRATION_FAILED)
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Registration action requires username, password, IP, port, and domain.")
                    // ====== 修正点：调用公共方法更新状态 ======
                    XmppConnectionManager.updateRegistrationState(XmppConnectionManager.RegistrationState.REGISTRATION_FAILED)
                }
            }
            // TODO: 可以添加其他动作，例如 ACTION_SEND_MESSAGE
        }

        // START_STICKY 表示如果系统杀死服务，当有足够的内存时会尝试重新创建它。
        // 但通常不带原始 Intent，所以适合用于长期运行的服务。
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "XmppConnectionService onDestroy")
        serviceScope.launch {
            XmppConnectionManager.disconnect() // 服务销毁时断开连接
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // 对于我们的用例，Service 不与 Activity 绑定（使用事件或 LiveData 通信），所以返回 null
        return null
    }

    // 创建通知渠道 (Android 8.0 Oreo 及以上需要)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "XMPP Service Channel",
                NotificationManager.IMPORTANCE_LOW // 低优先级，不打扰用户
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    // 创建前台服务通知
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Montext IM 运行中")
            .setContentText("正在连接到服务器...")
            .setSmallIcon(R.mipmap.ic_launcher) // 你的应用图标
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // 观察 XMPP 连接和登录状态，并更新通知或广播状态
    private fun observeConnectionAndLoginState() {
        serviceScope.launch {
            XmppConnectionManager.connectionState.collectLatest { state ->
                Log.d(TAG, "Connection State Changed: $state")
                // 可以更新通知文本，或者发送广播给 UI
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val builder = NotificationCompat.Builder(this@XmppConnectionService, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_LOW)

                when (state) {
                    XmppConnectionManager.ConnectionState.CONNECTED -> {
                        builder.setContentTitle("Montext IM 已连接")
                        builder.setContentText("等待登录...")
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                    }
                    XmppConnectionManager.ConnectionState.DISCONNECTED -> {
                        builder.setContentTitle("Montext IM 已断开")
                        builder.setContentText("尝试重新连接...")
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                    }
                    XmppConnectionManager.ConnectionState.CONNECTING -> {
                        builder.setContentTitle("Montext IM 正在连接")
                        builder.setContentText("正在连接服务器...")
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                    }
                    else -> {}
                }
            }
        }

        serviceScope.launch {
            XmppConnectionManager.loginState.collectLatest { state ->
                Log.d(TAG, "Login State Changed: $state")
                // 登录状态变化，可能需要通知 UI 跳转或显示错误
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val builder = NotificationCompat.Builder(this@XmppConnectionService, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_LOW)

                when (state) {
                    XmppConnectionManager.LoginState.LOGGED_IN -> {
                        builder.setContentTitle("Montext IM 已登录")
                        builder.setContentText("服务正常运行中")
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                        // TODO: 登录成功后，可以通过广播或 EventBus 通知 LoginActivity 跳转到主界面
                    }
                    XmppConnectionManager.LoginState.LOGIN_FAILED -> {
                        builder.setContentTitle("Montext IM 登录失败")
                        builder.setContentText("请检查用户名或密码")
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                        // TODO: 登录失败后，需要通知 LoginActivity 显示错误信息
                    }
                    XmppConnectionManager.LoginState.LOGGING_IN -> {
                        builder.setContentTitle("Montext IM 正在登录")
                        builder.setContentText("正在验证账户...")
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                    }
                    else -> {}
                }
            }
        }
    }
}