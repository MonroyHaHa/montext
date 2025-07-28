package com.monroy.montext.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.monroy.montext.R // 确保导入 R
import com.monroy.montext.databinding.ActivityMainBinding // 导入生成的 binding 类

/**
 * ActivityMainBinding.inflate(layoutInflater): 使用 View Binding 方便地访问布局中的视图。
 *
 * loadFragment(MessageFragment()): 在 Activity 首次创建时（savedInstanceState == null）加载默认的 MessageFragment。
 *
 * binding.bottomNavigationView.setOnItemSelectedListener { item -> ... }: 监听底部导航栏的点击事件。
 *
 * when (item.itemId): 根据点击的菜单项 ID，调用 loadFragment 方法加载对应的 Fragment。
 *
 * loadFragment(fragment: Fragment): 这是一个辅助函数，用于执行 Fragment 事务。
 *
 * supportFragmentManager.beginTransaction(): 开始一个 Fragment 事务。
 *
 * .replace(R.id.nav_host_fragment_container, fragment): 将 nav_host_fragment_container 中的当前 Fragment 替换为新的 Fragment。
 *
 * .commit(): 提交事务以应用更改。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 首次启动时加载默认 Fragment (消息界面)
        if (savedInstanceState == null) {
            loadFragment(MessageFragment())
        }

        // 设置底部导航栏点击事件
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_message -> {
                    loadFragment(MessageFragment())
                    true
                }
                R.id.nav_contacts -> {
                    loadFragment(ContactsFragment())
                    true
                }
                R.id.nav_my_profile -> {
                    loadFragment(MyProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment_container, fragment) // 替换 Fragment 容器中的内容
            .commit()
    }
}