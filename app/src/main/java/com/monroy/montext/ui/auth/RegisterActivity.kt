package com.monroy.montext.ui.auth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.monroy.montext.databinding.ActivityRegisterBinding
import com.monroy.montext.data.network.XmppConnectionManager
import com.monroy.montext.services.XmppConnectionService
import com.monroy.montext.utils.PreferenceManager // 确保导入 PreferenceManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val TAG = "RegisterActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeRegistrationState()
    }

    private fun setupListeners() {
        binding.btnRegisterAccount.setOnClickListener {
            attemptRegistration()
        }

        binding.btnBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun attemptRegistration() {
        val username = binding.etRegisterUsername.text.toString().trim()
        val password = binding.etRegisterPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        val realName = binding.etRealName.text.toString().trim() // 获取真实姓名
        val email = binding.etEmail.text.toString().trim()       // 获取电子邮件

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "用户名和密码是必填字段", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "密码长度不能少于6位", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "尝试注册用户: $username, 真实姓名: $realName, 邮箱: $email")
        Toast.makeText(this, "正在尝试注册...", Toast.LENGTH_SHORT).show()

        // 启动 XMPP 连接服务并尝试注册，传递所有字段
        startServiceAndRegister(username, password, realName, email)
    }

    private fun startServiceAndRegister(
        username: String,
        password: String,
        realName: String, // 新增参数
        email: String     // 新增参数
    ) {
        val serverIp = PreferenceManager.getServerIp(this)
        val serverPort = PreferenceManager.getServerPort(this)
        val serverDomain = PreferenceManager.getServerDomain(this)

        if (serverIp.isEmpty() || serverDomain.isEmpty()) {
            Toast.makeText(this, "请先设置服务器地址和域", Toast.LENGTH_LONG).show()
            return
        }

        val serviceIntent = Intent(this, XmppConnectionService::class.java).apply {
            putExtra("action", XmppConnectionService.ACTION_REGISTER)
            putExtra("username", username)
            putExtra("password", password)
            putExtra("real_name", realName) // 传递真实姓名
            putExtra("email", email)         // 传递电子邮件
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

    private fun observeRegistrationState() {
        lifecycleScope.launch {
            XmppConnectionManager.registrationState.collectLatest { state ->
                Log.d(TAG, "Activity sees Registration State: $state")
                when (state) {
                    XmppConnectionManager.RegistrationState.REGISTRATION_SUCCESS -> {
                        Toast.makeText(this@RegisterActivity, "注册成功！请登录", Toast.LENGTH_LONG).show()
                        val resultIntent = Intent().apply {
                            putExtra("registered_username", binding.etRegisterUsername.text.toString().trim())
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                    XmppConnectionManager.RegistrationState.REGISTRATION_FAILED -> {
                        Toast.makeText(this@RegisterActivity, "注册失败，请稍后再试或联系管理员", Toast.LENGTH_LONG).show()
                    }
                    XmppConnectionManager.RegistrationState.REGISTERING -> {
                        Toast.makeText(this@RegisterActivity, "正在注册...", Toast.LENGTH_SHORT).show()
                    }
                    else -> { /* IDLE 状态不做特殊处理 */ }
                }
            }
        }
    }
}