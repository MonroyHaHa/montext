package com.monroy.montext.ui.auth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope // 导入 lifecycleScope
import com.monroy.montext.R
import com.monroy.montext.databinding.ActivityLoginBinding
import com.monroy.montext.data.network.XmppConnectionManager // 导入 XmppConnectionManager
import com.monroy.montext.services.XmppConnectionService
import com.monroy.montext.ui.main.MainActivity // TODO: 主界面的 Activity, 待创建
import com.monroy.montext.utils.PreferenceManager
import kotlinx.coroutines.flow.collectLatest // 导入 collectLatest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val TAG = "LoginActivity"

    companion object {
        private const val REQUEST_CODE_REGISTER = 1001
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 绑定视图
        val etUsername = binding.etUsername
        val etPassword = binding.etPassword
        val cbRememberPassword = binding.cbRememberPassword
        val btnLogin = binding.btnLogin
        val btnRegister = binding.btnRegister
        val btnServerConfig = binding.btnServerConfig

        // 1. 加载记住的密码（如果存在）
        val savedUsername = PreferenceManager.getUsername(this)
        val savedPassword = PreferenceManager.getPassword(this)
        val rememberMe = PreferenceManager.getRememberMe(this)

        if (rememberMe && savedUsername.isNotEmpty() && savedPassword.isNotEmpty()) {
            etUsername.setText(savedUsername)
            etPassword.setText(savedPassword)
            cbRememberPassword.isChecked = true
        }

        // 2. 登录按钮点击事件
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入账户和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 保存记住密码的状态
            PreferenceManager.setRememberMe(this, cbRememberPassword.isChecked)
            if (cbRememberPassword.isChecked) {
                PreferenceManager.setUsername(this, username)
                PreferenceManager.setPassword(this, password)
            } else {
                PreferenceManager.clearLoginCredentials(this)
            }

            Log.d(TAG, "尝试登录: $username")
            Toast.makeText(this, "尝试登录...", Toast.LENGTH_SHORT).show()

            // 启动 XMPP 连接服务并尝试登录
            startXmppServiceAndLogin(username, password)
        }

        // 3. 注册按钮点击事件
        btnRegister.setOnClickListener {
            // TODO: 跳转到注册页面
            val intent = Intent(this, RegisterActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_REGISTER) // 使用 startActivityForResult
        }

        // 4. 服务器设置按钮点击事件
        btnServerConfig.setOnClickListener {
            showServerConfigDialog()
        }

        // 5. 观察 XMPP 连接和登录状态
        observeXmppStates()
    }

    // 显示服务器配置弹窗
    private fun showServerConfigDialog() {
        // ... (与之前相同，无需修改) ...
        val dialogView = layoutInflater.inflate(R.layout.dialog_server_config, null)
        val etIpAddress = dialogView.findViewById<EditText>(R.id.et_ip_address)
        val etPort = dialogView.findViewById<EditText>(R.id.et_port)
        val etDomain = dialogView.findViewById<EditText>(R.id.et_domain) // 添加域名的输入框

        // 预填充已保存的配置
        etIpAddress.setText(PreferenceManager.getServerIp(this))
        etPort.setText(PreferenceManager.getServerPort(this).toString())
        etDomain.setText(PreferenceManager.getServerDomain(this))

        AlertDialog.Builder(this)
            .setTitle("服务器设置")
            .setView(dialogView)
            .setPositiveButton("保存") { dialog, _ ->
                val ipAddress = etIpAddress.text.toString().trim()
                val portStr = etPort.text.toString().trim()
                val domain = etDomain.text.toString().trim()

                if (ipAddress.isEmpty() || portStr.isEmpty() || domain.isEmpty()) {
                    Toast.makeText(this, "IP地址、端口和域不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val port = portStr.toIntOrNull()
                if (port == null || port <= 0 || port > 65535) {
                    Toast.makeText(this, "请输入有效的端口号 (1-65535)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                PreferenceManager.setServerIp(this, ipAddress)
                PreferenceManager.setServerPort(this, port)
                PreferenceManager.setServerDomain(this, domain) // 保存域名
                Toast.makeText(this, "服务器设置已保存", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun startXmppServiceAndLogin(username: String, password: String) {
        val serverIp = PreferenceManager.getServerIp(this)
        val serverPort = PreferenceManager.getServerPort(this)
        val serverDomain = PreferenceManager.getServerDomain(this)

        if (serverIp.isEmpty() || serverDomain.isEmpty()) {
            Toast.makeText(this, "请先设置服务器地址和域", Toast.LENGTH_LONG).show()
            return
        }

        val serviceIntent = Intent(this, XmppConnectionService::class.java).apply {
            putExtra("action", XmppConnectionService.ACTION_LOGIN) // 传递操作类型
            putExtra("username", username)
            putExtra("password", password)
            putExtra("server_ip", serverIp)
            putExtra("server_port", serverPort)
            putExtra("server_domain", serverDomain)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_REGISTER && resultCode == RESULT_OK) {
            val registeredUsername = data?.getStringExtra("registered_username")
            registeredUsername?.let {
                binding.etUsername.setText(it) // 自动填充注册成功的用户名
                binding.etPassword.setText("") // 清空密码，让用户重新输入
                binding.cbRememberPassword.isChecked = false // 默认不记住密码
            }
        }
    }

    // 新增方法：观察 XMPP 连接和登录状态
    private fun observeXmppStates() {
        lifecycleScope.launch {
            XmppConnectionManager.connectionState.collectLatest { state ->
                Log.d(TAG, "Activity sees Connection State: $state")
                when (state) {
                    XmppConnectionManager.ConnectionState.CONNECTED -> {
                        Toast.makeText(this@LoginActivity, "XMPP 连接成功", Toast.LENGTH_SHORT).show()
                        // 如果连接成功但未登录，等待登录状态变化
                    }
                    XmppConnectionManager.ConnectionState.DISCONNECTED -> {
                        Toast.makeText(this@LoginActivity, "XMPP 已断开连接", Toast.LENGTH_SHORT).show()
                    }
                    XmppConnectionManager.ConnectionState.CONNECTING -> {
                        Toast.makeText(this@LoginActivity, "XMPP 正在连接...", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            XmppConnectionManager.loginState.collectLatest { state ->
                Log.d(TAG, "Activity sees Login State: $state")
                when (state) {
                    XmppConnectionManager.LoginState.LOGGED_IN -> {
                        Toast.makeText(this@LoginActivity, "登录成功！", Toast.LENGTH_SHORT).show()
                        // TODO: 登录成功，跳转到主界面
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish() // 关闭登录页
                    }
                    XmppConnectionManager.LoginState.LOGIN_FAILED -> {
                        Toast.makeText(this@LoginActivity, "登录失败，请检查账户或密码", Toast.LENGTH_LONG).show()
                        // 登录失败后，服务可能会断开连接，状态会回到 DISCONNECTED/LOGGED_OUT
                    }
                    XmppConnectionManager.LoginState.LOGGING_IN -> {
                        Toast.makeText(this@LoginActivity, "正在登录...", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }
}