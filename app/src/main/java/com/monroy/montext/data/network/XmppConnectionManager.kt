package com.monroy.montext.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.ConnectionListener
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.RosterEntry
import org.jivesoftware.smack.roster.RosterListener
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smackx.iqregister.AccountManager
import org.jivesoftware.smackx.vcardtemp.VCardManager
import org.jivesoftware.smackx.vcardtemp.packet.VCard
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.Jid // 保持导入 Jid
import org.jxmpp.jid.impl.JidCreate // ***新增此导入，用于创建Jid***
import org.jxmpp.jid.parts.Localpart
import org.jxmpp.jid.parts.Resourcepart
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.net.InetAddress // 即使 setHost 可以接受 String，为了以防万一和清晰，加上这个导入也无妨
import kotlinx.coroutines.SupervisorJob
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smack.roster.packet.RosterPacket
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch



object XmppConnectionManager {

    private val TAG = "XmppConnectionManager"
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection: XMPPTCPConnection? = null

    // 连接状态
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 登录状态
    private val _loginState = MutableStateFlow(LoginState.LOGGED_OUT)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    // 注册状态
    private val _registrationState = MutableStateFlow(RegistrationState.IDLE)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    // --- Roster (好友列表) 状态 ---
    // Contact 数据类新增 avatar 字段
    data class Contact(
        val jid: BareJid,
        val name: String,
        val status: Presence.Mode? = null,
        val isOnline: Boolean = false,
        val avatar: Bitmap? = null // 新增头像字段
    )

    private val _rosterState = MutableStateFlow<List<Contact>>(emptyList())
    val rosterState: StateFlow<List<Contact>> = _rosterState.asStateFlow()

    private var roster: Roster? = null
    private val rosterListener = object : RosterListener {
        override fun entriesAdded(addresses: Collection<Jid>) {
            Log.d(TAG, "RosterListener: entriesAdded: $addresses")
            for (jid in addresses) {
                val entry = roster?.getEntry(jid.asBareJid())
                Log.d(TAG, "  -> Added JID: ${jid.asBareJid()}, Type: ${entry?.type}, Status: ${entry?.isSubscriptionPending}") // <--- 添加此日志
            }
            updateRoster()

        }

        override fun entriesUpdated(addresses: Collection<Jid>) {
            Log.d(TAG, "RosterListener: entriesUpdated: $addresses")
            for (jid in addresses) {
                val entry = roster?.getEntry(jid.asBareJid())
                Log.d(TAG, "  -> Updated JID: ${jid.asBareJid()}, Type: ${entry?.type}, Status: ${entry?.isSubscriptionPending}") // <--- 添加此日志
            }
            updateRoster()
        }

        override fun entriesDeleted(addresses: Collection<Jid>) {
            Log.d(TAG, "RosterListener: entriesDeleted: $addresses")
            updateRoster()
        }

        override fun presenceChanged(presence: Presence) {
            // 这通常是好友状态变化，不直接影响列表条目
            Log.d(TAG, "RosterListener: presenceChanged: ${presence.from}: ${presence.mode}")
            updateRoster() // 更新好友状态也需要更新UI
        }
    }
    // ------------------------------------

    // --- VCardManager 相关变量 ---
    private var vCardManager: VCardManager? = null
    // 使用 ConcurrentHashMap 缓存已加载的头像，key 为 JID (BareJid)，value 为 Bitmap
    private val avatarCache = ConcurrentHashMap<BareJid, Bitmap>()
    // --------------------------------------

    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }

    enum class LoginState {
        LOGGED_IN, LOGGING_IN, LOGIN_FAILED, LOGGED_OUT
    }

    enum class RegistrationState {
        IDLE, REGISTERING, REGISTRATION_SUCCESS, REGISTRATION_FAILED
    }

    fun initializeAndConnect(serverIp: String, port: Int, domain: String) {
        if (connection != null && (connection!!.isConnected || connection!!.isAuthenticated)) {
            Log.d(TAG, "Already connected or authenticated.")
            _connectionState.value = ConnectionState.CONNECTED
            return
        }

        if (_connectionState.value == ConnectionState.CONNECTING) {
            Log.d(TAG, "Already attempting connection.")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        _loginState.value = LoginState.LOGGING_IN // 连接中也意味着尝试登录中
        org.jivesoftware.smack.roster.Roster.setRosterLoadedAtLoginDefault(false)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val configBuilder = XMPPTCPConnectionConfiguration.builder()
                    .setHost(serverIp)
                    .setPort(port)
                    .setXmppDomain(domain)
                    // 修正：使用 required 强制安全连接，如果Openfire支持TLS
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
                    .setSendPresence(true)
                    .setConnectTimeout(30000)

                // 移除不需要或可能导致安全问题的配置，如 setHostnameVerifier 和 setCustomX509TrustManager
                // 在生产环境中，应正确配置证书信任。

                connection = XMPPTCPConnection(configBuilder.build())

                connection?.addConnectionListener(object : ConnectionListener {
                    override fun connected(connection: XMPPConnection?) {
                        Log.d(TAG, "XMPP Connected!")
                        _connectionState.value = ConnectionState.CONNECTED
                    }

                    override fun authenticated(connection: XMPPConnection?, resumed: Boolean) {
                        Log.d(TAG, "XMPP Authenticated! Resumed: $resumed")
                        _loginState.value = LoginState.LOGGED_IN
                        if (_registrationState.value == RegistrationState.REGISTERING) {
                            _registrationState.value = RegistrationState.REGISTRATION_SUCCESS
                        }

                        // 清除并重置现有实例和监听器，确保每次认证都是从干净状态开始。
                        roster?.removeRosterListener(rosterListener)
                        // 移除旧的订阅监听器，避免重复添加
                        subscriptionListener?.let { connection?.removeStanzaListener(it) }

                        roster = null
                        vCardManager = null
                        avatarCache.clear()

                        // 重新初始化 Roster 和 VCardManager
                        initRoster()
                        initVCardManager()

                        // === 添加自动接受订阅请求的逻辑 ===
                        subscriptionListener = StanzaListener { stanza ->
                            if (stanza is Presence) {
                                when (stanza.type) {
                                    Presence.Type.subscribe -> {
                                        // 收到来自其他用户的订阅请求
                                        Log.d(TAG, "Received subscription request from: ${stanza.from}")

                                        // 1. 自动接受订阅 (发送 'subscribed' 回复)
                                        val subscribedPresence = Presence(stanza.from, Presence.Type.subscribed)
                                        connection?.sendStanza(subscribedPresence)
                                        Log.d(TAG, "Sent 'subscribed' response to: ${stanza.from}") // <--- 添加此日志

                                        // 2. 将对方添加到我们的花名册（如果尚未添加），并确保我们也订阅对方。
                                        // 这将促使订阅状态变为 'both'。
                                        val currentRoster = Roster.getInstanceFor(connection)
                                        val friendJidBare = stanza.from.asBareJid()

                                        if (!currentRoster.contains(friendJidBare)) {
                                            try {
                                                // 使用对方的 localpart 作为昵称，如果没有提供自定义昵称
                                                val nickname = friendJidBare.localpartOrNull?.toString() ?: ""
                                                currentRoster.createEntry(friendJidBare, nickname, null)
                                                Log.d(TAG, "Created roster entry for ${friendJidBare} after receiving subscribe request and SENT OUR SUBSCRIBE BACK.") // <--- 修改并确认此日志
                                            } catch (e: XMPPException.XMPPErrorException) {
                                                if (e.stanzaError.condition == StanzaError.Condition.conflict) {
                                                    Log.w(TAG, "Conflict adding roster entry for incoming subscribe, entry likely exists: ${friendJidBare}")
                                                } else {
                                                    Log.e(TAG, "Error creating roster entry for incoming subscribe: ${e.message}", e)
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Generic error creating roster entry for incoming subscribe: ${e.message}", e)
                                            }
                                        } else {
                                            // 如果已经在花名册中，确保订阅类型是 'both'
                                            val entry = currentRoster.getEntry(friendJidBare)
                                            if (entry.type != RosterPacket.ItemType.both) {
                                                Log.d(TAG, "Roster entry for ${friendJidBare} exists, ensuring mutual subscription by re-sending subscribe.")
                                                val resubscribePresence = Presence(friendJidBare, Presence.Type.subscribe)
                                                connection?.sendStanza(resubscribePresence)
                                                Log.d(TAG, "Re-sent subscribe presence to ${friendJidBare} to achieve 'both' status.") // <--- 添加此日志
                                            }
                                        }

                                        updateRoster() // 更新 UI 以反映新朋友或订阅状态
                                    }
                                    Presence.Type.unsubscribed -> {
                                        // 收到取消订阅通知，通常表示对方将我们从好友列表中移除
                                        Log.d(TAG, "Received unsubscribe from: ${stanza.from}")
                                        // 此时 RosterListener 的 entriesDeleted 应该会处理花名册的更新
                                        updateRoster()
                                    }
                                    Presence.Type.error -> {
                                        Log.e(TAG, "Presence error from ${stanza.from}: ${stanza.error}")
                                    }
                                    else -> {
                                        // 其他 Presence 类型（如 available, unavailable 等）会由 RosterListener.presenceChanged 隐式处理
                                    }
                                }
                            }
                        }
                        // 将监听器添加到连接中，只处理 Presence 节
                        connection?.addAsyncStanzaListener(subscriptionListener, StanzaTypeFilter(Presence::class.java))
                    }

                    override fun connectionClosed() {
                        Log.d(TAG, "XMPP Connection Closed!")
                        _connectionState.value = ConnectionState.DISCONNECTED
                        _loginState.value = LoginState.LOGGED_OUT
                        _registrationState.value = RegistrationState.IDLE
                        roster?.removeRosterListener(rosterListener)
                        roster = null
                        _rosterState.value = emptyList() // 清空好友列表
                        // --- 连接关闭时清理 VCardManager 和缓存 ---
                        vCardManager = null
                        avatarCache.clear()
                        // ------------------------------------
                    }

                    override fun connectionClosedOnError(e: Exception?) {
                        Log.e(TAG, "XMPP Connection Closed On Error: ${e?.message}", e)
                        _connectionState.value = ConnectionState.DISCONNECTED
                        _loginState.value = LoginState.LOGIN_FAILED
                        _registrationState.value = RegistrationState.REGISTRATION_FAILED
                        roster?.removeRosterListener(rosterListener)
                        roster = null
                        _rosterState.value = emptyList() // 清空好友列表
                        // --- 连接错误时清理 VCardManager 和缓存 ---
                        vCardManager = null
                        avatarCache.clear()
                        // ------------------------------------
                    }

                    // *** 移除以下重连回调，因为在 Smack 4.4.x 的 ConnectionListener 接口中它们不再存在 ***
                    // 如果需要重连逻辑，需要单独实现或使用 Smack 内置的 ReconnectionManager
                    // override fun reconnectionSuccessful() { /* ... */ }
                    // override fun reconnectingIn(seconds: Int) { /* ... */ }
                    // override fun reconnectionFailed(e: Exception?) { /* ... */ }
                })

                Log.d(TAG, "XMPP Connection attempt started.")
                connection?.connect()

            } catch (e: XMPPException) {
                Log.e(TAG, "XMPPException during connection: ${e.message}", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                _loginState.value = LoginState.LOGIN_FAILED
                _registrationState.value = RegistrationState.REGISTRATION_FAILED
            } catch (e: SmackException) {
                Log.e(TAG, "SmackException during connection: ${e.message}", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                _loginState.value = LoginState.LOGIN_FAILED
                _registrationState.value = RegistrationState.REGISTRATION_FAILED
            } catch (e: IOException) {
                Log.e(TAG, "IOException during connection: ${e.message}", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                _loginState.value = LoginState.LOGIN_FAILED
                _registrationState.value = RegistrationState.REGISTRATION_FAILED
            } catch (e: InterruptedException) {
                Log.e(TAG, "InterruptedException during connection: ${e.message}", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                _loginState.value = LoginState.LOGIN_FAILED
                _registrationState.value = RegistrationState.REGISTRATION_FAILED
                Thread.currentThread().interrupt() // 重新中断线程
            }
        }
    }

    fun login(username: String, password: String) {
        if (connection == null || !connection!!.isConnected) {
            Log.e(TAG, "Cannot login: Not connected to XMPP server.")
            _loginState.value = LoginState.LOGIN_FAILED
            return
        }
        if (connection!!.isAuthenticated) {
            Log.d(TAG, "Already authenticated.")
            _loginState.value = LoginState.LOGGED_IN
            return
        }

        _loginState.value = LoginState.LOGGING_IN

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 登录时设置资源，例如 "AndroidApp"
                connection?.login(username, password, Resourcepart.from("AndroidApp"))
                // 登录成功会触发 authenticated 回调，更新 _loginState
                Log.d(TAG, "XMPP Login attempt started.")
            } catch (e: XMPPException) {
                Log.e(TAG, "XMPPException during login: ${e.message}", e)
                _loginState.value = LoginState.LOGIN_FAILED
            } catch (e: SmackException) {
                Log.e(TAG, "SmackException during login: ${e.message}", e)
                _loginState.value = LoginState.LOGIN_FAILED
            } catch (e: IOException) {
                Log.e(TAG, "IOException during login: ${e.message}", e)
                _loginState.value = LoginState.LOGIN_FAILED
            } catch (e: InterruptedException) {
                Log.e(TAG, "InterruptedException during login: ${e.message}", e)
                _loginState.value = LoginState.LOGIN_FAILED
                Thread.currentThread().interrupt()
            }
        }
    }

    fun register(username: String, password: String, realName: String, email: String) {
        if (connection == null || !connection!!.isConnected) {
            Log.e(TAG, "Cannot register: Not connected to XMPP server.")
            _registrationState.value = RegistrationState.REGISTRATION_FAILED
            return
        }
        if (_registrationState.value == RegistrationState.REGISTERING) {
            Log.d(TAG, "Already attempting registration.")
            return
        }

        _registrationState.value = RegistrationState.REGISTERING

        CoroutineScope(Dispatchers.IO).launch { // 注册也应在IO线程执行
            try {
                val accountManager = AccountManager.getInstance(connection)
                if (accountManager.supportsAccountCreation()) {
                    val attributes = mutableMapOf<String, String>()
                    if (realName.isNotEmpty()) {
                        attributes["name"] = realName
                    }
                    if (email.isNotEmpty()) {
                        attributes["email"] = email
                    }

                    accountManager.createAccount(Localpart.from(username), password, attributes)
                    Log.d(TAG, "User '$username' registered successfully.")
                    updateRegistrationState(RegistrationState.REGISTRATION_SUCCESS)

                } else {
                    Log.e(TAG, "Server does not support in-band registration.")
                    updateRegistrationState(RegistrationState.REGISTRATION_FAILED)
                }
            } catch (e: XMPPException) {
                Log.e(TAG, "XMPPException during registration: ${e.message}", e)
                updateRegistrationState(RegistrationState.REGISTRATION_FAILED)
            } catch (e: SmackException) {
                Log.e(TAG, "SmackException during registration: ${e.message}", e)
                updateRegistrationState(RegistrationState.REGISTRATION_FAILED)
            } catch (e: IOException) {
                Log.e(TAG, "IOException during registration: ${e.message}", e)
                updateRegistrationState(RegistrationState.REGISTRATION_FAILED)
            } catch (e: InterruptedException) {
                Log.e(TAG, "InterruptedException during registration: ${e.message}", e)
                updateRegistrationState(RegistrationState.REGISTRATION_FAILED)
                Thread.currentThread().interrupt()
            }
        }
    }

    // 公共方法：更新连接状态
    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    // 公共方法：更新登录状态
    fun updateLoginState(state: LoginState) {
        _loginState.value = state
    }

    // 公共方法：更新注册状态
    fun updateRegistrationState(state: RegistrationState) {
        _registrationState.value = state
    }

    fun disconnect() {
        if (connection != null && connection!!.isConnected) {
            Log.d(TAG, "Disconnecting XMPP connection.")
            connection?.disconnect()
        } else {
            Log.d(TAG, "No active XMPP connection to disconnect.")
            // 确保即使没有连接，状态也正确
            _connectionState.value = ConnectionState.DISCONNECTED
            _loginState.value = LoginState.LOGGED_OUT
            _registrationState.value = RegistrationState.IDLE
        }
        // 无论连接是否存在，都清理 Roster 和 VCard 相关的实例
        roster?.removeRosterListener(rosterListener)
        roster = null
        _rosterState.value = emptyList()
        // --- 确保断开时清理 VCardManager 和缓存 ---
        vCardManager = null
        avatarCache.clear()
        // ------------------------------------
    }

    /**
     * 获取当前的 XMPP 连接实例。
     */
    fun getConnection(): XMPPTCPConnection? {
        return connection
    }

    /**
     * 检查是否已连接。
     */
    fun isConnected(): Boolean {
        return connection?.isConnected ?: false
    }

    /**
     * 检查是否已认证（登录）。
     */
    fun isAuthenticated(): Boolean {
        return connection?.isAuthenticated ?: false
    }

    // --- Roster 管理方法 ---

    private fun initRoster() {
        if (connection == null || !connection!!.isAuthenticated) {
            Log.e(TAG, "Cannot init Roster: Not authenticated.")
            Log.e(TAG, "initRoster: Connection is not authenticated, cannot initialize Roster.")
            return
        }
        if (roster != null) {
            Log.d(TAG, "Roster already initialized.")
            return
        }
        try {
            roster = Roster.getInstanceFor(connection)
            roster?.removeRosterListener(rosterListener)
            roster?.addRosterListener(rosterListener)
            Log.d(TAG, "initRoster: Roster initialized. isLoaded=${roster?.isLoaded}, current entries=${roster?.entries?.size} (before forced reload).")
            // 设置订阅模式，例如自动接受所有订阅请求
            roster?.setSubscriptionMode(Roster.SubscriptionMode.accept_all)

            // === 强制 Roster 重新加载并等待完成，并添加更多日志 ===
            if (roster != null) {
                try {
                    // 打印加载前的状态
                    Log.d(TAG, "initRoster: Roster initialized. isLoaded=${roster!!.isLoaded}, current entries=${roster!!.entries.size} (before forced reload).")

                    // 强制重新加载并等待，无论 isLoaded 状态如何
                    roster!!.reloadAndWait()

                    // 打印加载后的状态
                    Log.d(TAG, "initRoster: Roster reloaded and waited. Entries after forced reload: ${roster!!.entries.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reloading Roster in initRoster: ${e.message}", e)
                }
            } else {
                Log.e(TAG, "initRoster: Roster instance is null after getInstanceFor.")
            }
            // ======================================

            // 现在Roster应该已经加载完毕，再进行更新
            updateRoster() // 初始化后立即更新一次好友列表
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Roster: ${e.message}", e)
        }
    }

    private fun updateRoster() {
        if (roster == null) {
            _rosterState.value = emptyList()
            Log.w(TAG, "updateRoster: Roster is null, clearing contacts.") // 添加此日志
            return
        }

        val currentContacts = mutableListOf<Contact>()
        val entries = roster!!.entries

        Log.d(TAG, "updateRoster: Roster instance is not null. Current entries count from Roster: ${entries.size}") // 新增日志

        CoroutineScope(Dispatchers.IO).launch { // 在 IO 协程中执行，因为获取头像可能耗时
            for (entry: RosterEntry in entries) {
                val bareJid = entry.jid.asBareJid()
                // 使用昵称，如果没有则使用JID的Localpart
                val name = entry.name ?: bareJid.localpartOrThrow.asUnescapedString()
                val presence = roster!!.getPresence(bareJid)
                // 修正：直接判断 type 是否为 available
                val isOnline = presence.type == Presence.Type.available
                // removed presence.mode != Presence.Mode.unavailable

                // --- 获取头像，使用 getVCardAvatar ---
                val avatar = getVCardAvatar(bareJid)
                // --------------------

                currentContacts.add(Contact(bareJid, name, presence.mode, isOnline, avatar))
            }

            // 按在线状态和名称排序
            val sortedContacts = currentContacts.sortedWith(compareBy<Contact> { !it.isOnline }
                .thenBy { it.name.lowercase() })

            _rosterState.value = sortedContacts
            Log.d(TAG, "Roster updated. Total contacts: ${sortedContacts.size}. Contacts data pushed to StateFlow.") // 修改日志
        }
    }

    fun addContact(jidString: String, nickname: String = "") {
        if (connection == null || !connection!!.isAuthenticated) {
            Log.e(TAG, "Cannot add contact: Not authenticated.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 修正：使用 JidCreate.bareFrom
                val jid = JidCreate.bareFrom(jidString)
                roster?.createEntry(jid, nickname, null) // 添加联系人到花名册
                Log.d(TAG, "Sent add contact request for $jidString")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding contact $jidString: ${e.message}", e)
            }
        }
    }

    fun removeContact(jid: BareJid) {
        if (connection == null || !connection!!.isAuthenticated) {
            Log.e(TAG, "Cannot remove contact: Not authenticated.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entry = roster?.getEntry(jid)
                if (entry != null) {
                    roster?.removeEntry(entry) // 从花名册中删除联系人
                    Log.d(TAG, "Removed contact $jid")
                } else {
                    Log.w(TAG, "Contact $jid not found in roster.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing contact $jid: ${e.message}", e)
            }
        }
    }

    // --- VCardManager 相关方法 ---

    // 初始化 VCardManager
    private fun initVCardManager() {
        if (connection == null || !connection!!.isAuthenticated) {
            Log.e(TAG, "Cannot init VCardManager: Not authenticated.")
            return
        }
        if (vCardManager != null) {
            Log.d(TAG, "VCardManager already initialized.")
            return
        }
        try {
            vCardManager = VCardManager.getInstanceFor(connection)
            Log.d(TAG, "VCardManager initialized.")
            // VCardManager 没有像 AvatarManager 那样的全局监听器，头像更新需要主动查询或依赖 Presence 中的 vCard hash
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing VCardManager: ${e.message}", e)
        }
    }

    /**
     * 从缓存或服务器获取指定 JID 的头像 (通过 VCard)。
     * 这是一个耗时操作，应该在 IO 线程中调用。
     * @param jid 好友的 BareJid
     * @return Bitmap 形式的头像，如果获取失败或不存在则返回 null
     */
    private suspend fun getVCardAvatar(jid: BareJid): Bitmap? {
        // 首先从内存缓存中查找
        if (avatarCache.containsKey(jid)) {
            return avatarCache[jid]
        }

        // 如果缓存中没有，尝试从 VCardManager 获取
        return try {
            // 修正：loadVCard 接受 BareJid，问题可能出在其他地方或IDE的缓存，确保 JidCreate.bareFrom 的使用正确
            val entityBareJid = JidCreate.entityBareFrom(jid.toString())
            val vcard = vCardManager?.loadVCard(entityBareJid)
            val avatarBytes = vcard?.avatar // 获取 vCard 中的头像字节数组

            if (avatarBytes != null && avatarBytes.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.size)
                bitmap?.let {
                    avatarCache[jid] = it // 缓存头像
                    Log.d(TAG, "Fetched and cached vCard avatar for $jid")
                }
                bitmap
            } else {
                Log.d(TAG, "No vCard avatar found for $jid or bytes are empty.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting vCard avatar for $jid: ${e.message}", e)
            null
        }
    }

    /**
     * 上传当前用户的头像 (通过 VCard)。
     * @param avatarBitmap 要上传的 Bitmap 图像
     */
    fun uploadMyAvatar(avatarBitmap: Bitmap) {
        if (connection == null || !connection!!.isAuthenticated) {
            Log.e(TAG, "Cannot upload avatar: Not authenticated.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (vCardManager == null) {
                    Log.e(TAG, "VCardManager not initialized for upload.")
                    return@launch
                }

                // 获取当前用户的 vCard
                val myVCard = vCardManager?.loadVCard() // 不带 JID 参数表示获取自己的 vCard
                myVCard?.let { vcard ->
                    // 将 Bitmap 转换为 byte 数组
                    val stream = ByteArrayOutputStream()
                    avatarBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) // 压缩为 PNG 格式
                    val byteArray = stream.toByteArray()

                    vcard.avatar = byteArray // 设置头像
                    vCardManager?.saveVCard(vcard) // 保存 vCard 到服务器
                    // 更新自己的头像缓存，注意：connection!!.user 是一个 Jid 对象，需要转为 BareJid
                    connection?.user?.asBareJid()?.let { selfJid ->
                        avatarCache[selfJid] = avatarBitmap
                    }
                    updateRoster() // 通知 Roster 更新以显示自己的新头像

                    Log.d(TAG, "Uploaded avatar for current user.")
                } ?: run {
                    Log.e(TAG, "Failed to load current user's VCard for avatar upload.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading avatar: ${e.message}", e)
            }
        }
    }

    private var subscriptionListener: StanzaListener? = null
    /**
     * 向指定 JID 发送添加好友请求。
     * 这会创建一个 Roster Entry 并发送一个 Presence.Type.subscribe 节。
     * @param friendJidString 朋友的完整 JID 字符串，例如 "user@example.com"
     * @param nickname 可选的好友昵称。
     */
    suspend fun addFriend(friendJidString: String, nickname: String? = null) {
        if (connection == null || !connection!!.isAuthenticated) {
            Log.e(TAG, "addFriend: XMPP connection not authenticated.")
            throw IllegalStateException("XMPP connection not authenticated.")
        }

        // 确保 roster 已初始化。如果因某种原因为空，重新初始化它。
        if (roster == null) {
            initRoster() // 确保 roster 可用
        }
        val currentRoster = roster ?: run {
            Log.e(TAG, "addFriend: Roster is null after initRoster attempt, cannot add friend.")
            throw IllegalStateException("Roster not initialized.")
        }

        try {
            val friendJid = JidCreate.bareFrom(friendJidString)

            // 检查好友是否已在花名册中
            if (currentRoster.contains(friendJid)) {
                Log.w(TAG, "Friend ${friendJid.asBareJid()} is already in roster.")
                val entry = currentRoster.getEntry(friendJid)
                // 如果已在花名册中但不是 'both' 订阅，则重新发送订阅请求以尝试达到 'both' 状态
                if (entry.type != RosterPacket.ItemType.both) {
                    Log.d(TAG, "Re-sending subscribe presence to ${friendJid.asBareJid()} to ensure both subscription.")
                    val subscribePresence = Presence(friendJid, Presence.Type.subscribe)
                    connection?.sendStanza(subscribePresence)
                }
                updateRoster() // 更新 UI 以反映任何状态变化
                return
            }

            // 添加花名册条目。这会向朋友发送一个订阅请求 (Presence.Type.subscribe)。
            // 它还会将朋友添加到本地花名册中，初始订阅状态通常为 'none' 或 'from'。
            currentRoster.createEntry(friendJid, nickname, null) // 暂不指定分组
            Log.d(TAG, "Sent friend request to $friendJid. Waiting for mutual subscription.")
            Log.d(TAG, "Sent friend request (roster.createEntry) to $friendJid. Expecting mutual subscription negotiation.") // <--- 添加此日志

            // 发送请求后，更新 UI 以显示待处理的朋友。
            // RosterListener 最终会在订阅状态变为 mutual 时更新。
            updateRoster()

        } catch (e: Exception) {
            Log.e(TAG, "Error adding friend: ${e.message}", e)
            throw e // 重新抛出异常，由 ViewModel/UI 层处理
        }
    }

    // 当用户点击“添加好友”按钮时，调用：
    fun onAddFriendClicked(friendJid: String, nickname: String? = null) {
        applicationScope.launch { // 如果在 ViewModel 中，使用 viewModelScope
            try {
                addFriend(friendJid, nickname)
                // 提示用户请求已发送或好友已添加
                // Toast.makeText(context, "好友请求已发送或已添加！", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // 处理错误，例如显示错误消息
                // Toast.makeText(context, "添加好友失败: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("AddFriend", "Failed to add friend: ${e.message}", e)
            }
        }
    }
}