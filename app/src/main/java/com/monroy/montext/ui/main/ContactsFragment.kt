package com.monroy.montext.ui.main

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.monroy.montext.R
import com.monroy.montext.data.network.XmppConnectionManager // 确保导入 XmppConnectionManager
import com.monroy.montext.ui.contacts.ContactsAdapter // 确保导入 ContactsAdapter
import kotlinx.coroutines.launch

class ContactsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var contactsAdapter: ContactsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_contacts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.contactsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        contactsAdapter = ContactsAdapter { contact ->
            Toast.makeText(context, "点击了好友: ${contact.name}", Toast.LENGTH_SHORT).show()
            Log.d("ContactsFragment", "Clicked contact: ${contact.name} (${contact.jid})")
            // TODO: 在这里实现跳转到好友详情页或聊天页面的逻辑
        }
        recyclerView.adapter = contactsAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            XmppConnectionManager.rosterState.collect { contacts ->
                Log.d("ContactsFragment", "Roster state updated: ${contacts.size} contacts")
                contactsAdapter.submitList(contacts)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.contacts_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                Toast.makeText(context, "搜索好友功能待实现", Toast.LENGTH_SHORT).show()
                Log.d("ContactsFragment", "Search button clicked")
                true
            }
            R.id.action_add_friend -> {
                showAddFriendDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAddFriendDialog() {
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
        builder.setTitle("添加好友")

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        val jidInput = EditText(context)
        jidInput.hint = "好友 JID (例如: user@example.com)"
        layout.addView(jidInput)

        val nicknameInput = EditText(context)
        nicknameInput.hint = "昵称 (可选)"
        layout.addView(nicknameInput)

        builder.setView(layout)

        builder.setPositiveButton("添加") { dialog, _ ->
            val jid = jidInput.text.toString().trim()
            val nickname = nicknameInput.text.toString().trim()

            if (jid.isNotBlank()) {
                // *** 关键修改：从这里调用 XmppConnectionManager.onAddFriendClicked() ***
                XmppConnectionManager.onAddFriendClicked(jid, nickname)
                Toast.makeText(context, "已发送好友请求给: $jid", Toast.LENGTH_SHORT).show()
                Log.d("ContactsFragment", "Sent add friend request to: $jid with nickname: $nickname")
            } else {
                Toast.makeText(context, "JID 不能为空", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }
}