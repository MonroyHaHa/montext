package com.monroy.montext.utils


import android.content.Context
import android.content.SharedPreferences

object PreferenceManager {

    private const val PREF_NAME = "montext_prefs"
    private const val KEY_SERVER_IP = "server_ip"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_SERVER_DOMAIN = "server_domain" // 添加域名的 key
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password" // 生产环境请勿明文存储密码！这里仅为演示
    private const val KEY_REMEMBER_ME = "remember_me"

    // 获取 SharedPreferences 实例
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // 服务器 IP 地址
    fun setServerIp(context: Context, ip: String) {
        getSharedPreferences(context).edit().putString(KEY_SERVER_IP, ip).apply()
    }

    fun getServerIp(context: Context): String {
        return getSharedPreferences(context).getString(KEY_SERVER_IP, "") ?: ""
    }

    // 服务器端口
    fun setServerPort(context: Context, port: Int) {
        getSharedPreferences(context).edit().putInt(KEY_SERVER_PORT, port).apply()
    }

    fun getServerPort(context: Context): Int {
        // 默认 XMPP 客户端连接端口为 5222
        return getSharedPreferences(context).getInt(KEY_SERVER_PORT, 5222)
    }

    // 服务器域名
    fun setServerDomain(context: Context, domain: String) {
        getSharedPreferences(context).edit().putString(KEY_SERVER_DOMAIN, domain).apply()
    }

    fun getServerDomain(context: Context): String {
        return getSharedPreferences(context).getString(KEY_SERVER_DOMAIN, "") ?: ""
    }

    // 用户名
    fun setUsername(context: Context, username: String) {
        getSharedPreferences(context).edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(context: Context): String {
        return getSharedPreferences(context).getString(KEY_USERNAME, "") ?: ""
    }

    // 密码 (再次强调：生产环境请勿明文存储密码！)
    fun setPassword(context: Context, password: String) {
        getSharedPreferences(context).edit().putString(KEY_PASSWORD, password).apply()
    }

    fun getPassword(context: Context): String {
        return getSharedPreferences(context).getString(KEY_PASSWORD, "") ?: ""
    }

    // 记住密码
    fun setRememberMe(context: Context, remember: Boolean) {
        getSharedPreferences(context).edit().putBoolean(KEY_REMEMBER_ME, remember).apply()
    }

    fun getRememberMe(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(KEY_REMEMBER_ME, false)
    }

    // 清除登录凭据
    fun clearLoginCredentials(context: Context) {
        getSharedPreferences(context).edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }
}