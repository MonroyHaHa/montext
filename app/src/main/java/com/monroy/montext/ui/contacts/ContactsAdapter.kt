package com.monroy.montext.ui.contacts // 根据您的实际包路径调整

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.monroy.montext.R
import com.monroy.montext.data.network.XmppConnectionManager

// 定义点击事件监听器
class ContactsAdapter(private val onItemClick: (XmppConnectionManager.Contact) -> Unit) :
    ListAdapter<XmppConnectionManager.Contact, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = getItem(position)
        holder.bind(contact)
        holder.itemView.setOnClickListener { onItemClick(contact) }
    }

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val contactAvatar: ImageView = itemView.findViewById(R.id.contactAvatar)
        private val contactName: TextView = itemView.findViewById(R.id.contactName)
        private val contactStatus: TextView = itemView.findViewById(R.id.contactStatus)

        fun bind(contact: XmppConnectionManager.Contact) {
            contactName.text = contact.name

            // 设置在线状态
            val statusText = if (contact.isOnline) {
                "[在线] " + (contact.status?.name ?: "") // 显示 Presence.Mode 名称
            } else {
                "[离线]"
            }
            contactStatus.text = statusText

            // 设置头像
            if (contact.avatar != null) {
                contactAvatar.setImageBitmap(contact.avatar)
            } else {
                contactAvatar.setImageResource(R.drawable.ic_default_avatar) // 显示默认头像
            }
        }
    }

    private class ContactDiffCallback : DiffUtil.ItemCallback<XmppConnectionManager.Contact>() {
        override fun areItemsTheSame(oldItem: XmppConnectionManager.Contact, newItem: XmppConnectionManager.Contact): Boolean {
            return oldItem.jid == newItem.jid
        }

        override fun areContentsTheSame(oldItem: XmppConnectionManager.Contact, newItem: XmppConnectionManager.Contact): Boolean {
            return oldItem == newItem // 使用 data class 的自动 equals 实现
        }
    }
}