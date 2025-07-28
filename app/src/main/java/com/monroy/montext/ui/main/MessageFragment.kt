package com.monroy.montext.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.monroy.montext.ui.theme.MontextTheme // 确保导入您的主题

class MessageFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 使用 ComposeView 来承载 Compose UI
        return ComposeView(requireContext()).apply {
            // 设置 Compose 内容
            setContent {
                MontextTheme { // 使用您的应用主题
                    // A surface container using the 'background' color from the theme
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MessageScreen() // 这是我们自定义的 Composable 函数
                    }
                }
            }
        }
    }
}

// 这是一个简单的 Compose UI，用于演示
@Composable
fun MessageScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "消息界面 - Jetpack Compose")
    }
}
/**
 * ComposeView(requireContext()): 这是关键。ComposeView 是一个 Android View，它能够承载 Jetpack Compose UI。它允许您在传统的 View 系统中嵌入 Compose UI。
 *
 * setContent { ... }: 这个 lambda 块就是您定义 Compose UI 的地方。所有的 @Composable 函数都放在这里。
 *
 * MontextTheme { ... }: 这是您应用的主题包装器。确保您的 Compose UI 遵循 Material Design 3 规范和您的应用颜色/字体。
 *
 * Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background): 一个 Material Design 的表面容器，通常用于放置大部分 UI 元素。Modifier.fillMaxSize() 使其填充整个父容器。
 *
 * MessageScreen(), ContactsScreen(), MyProfileScreen(): 这些是自定义的 @Composable 函数。它们是构成 UI 的独立、可复用的单元。在这个阶段，它们只是显示一个简单的文本，但未来我们会在这里构建复杂的 UI。
 *
 * @Composable: 这是 Compose 的核心注解，标记一个函数可以用于构建 UI
 */