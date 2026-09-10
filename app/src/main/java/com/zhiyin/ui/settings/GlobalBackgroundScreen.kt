package com.zhiyin.ui.settings

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhiyin.data.ImageUtils
import com.zhiyin.ui.CardContainer
import com.zhiyin.ui.RubberBandBox
import com.zhiyin.ui.chat.ContentCopy
import com.zhiyin.ui.components.ImageCropperDialog
import com.zhiyin.ui.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalBackgroundScreen(appVm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenAspect = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()

    val enabled by appVm.globalBgEnabled.collectAsState()
    val path by appVm.globalBgPath.collectAsState()
    val acrylic by appVm.globalBgAcrylic.collectAsState()
    val blur by appVm.globalBgBlur.collectAsState()

    var cropSource by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isEmpty()) null else withContext(Dispatchers.IO) {
            val f = File(path)
            if (f.exists()) ImageUtils.decodeSampled(path, 1440)?.asImageBitmap() else null
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val copied = ContentCopy.copyToCache(context, it, "globalbg")
            if (copied != null) cropSource = copied.path else appVm.showToast("读取图片失败")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = { Text("全局背景", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
        )

        RubberBandBox(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                CardContainer {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("背景预览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "聊天界面不生效！",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(screenAspect.coerceIn(0.5f, 1f))
                                .clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap!!,
                                    contentDescription = "预览",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (acrylic) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)),
                                    )
                                    Text(
                                        "亚克力",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .align(Alignment.BottomEnd),
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Rounded.Wallpaper,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(36.dp),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "请选择背景图片",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { pick.launch("image/*") },
                                shape = RoundedCornerShape(50),
                                enabled = !saving,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (bitmap == null) "选择" else "更换")
                            }
                            OutlinedButton(
                                onClick = {
                                    appVm.setGlobalBgPath("")
                                    try {
                                        File(context.filesDir, "global_bg.jpg").delete()
                                    } catch (_: Exception) {
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                enabled = bitmap != null,
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("清除")
                            }
                        }
                    }
                }

                CardContainer {
                    Column {
                        SettingSwitchRow("启用全局背景", enabled) { appVm.setGlobalBgEnabled(it) }
                        Text(
                            if (path.isEmpty()) "需先选择一张背景图片" else "开启后所有非聊天页面生效",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        )
                    }
                }

                CardContainer {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingSwitchRow("亚克力效果", acrylic) { appVm.setGlobalBgAcrylic(it) }
                        Text(
                            "在背景图上叠加模糊效果",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        if (acrylic) {
                            Spacer(Modifier.height(8.dp))
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("模糊强度", modifier = Modifier.weight(1f))
                                    Text("${blur}dp", color = MaterialTheme.colorScheme.primary)
                                }
                                var sliderPos by remember(blur) { mutableFloatStateOf(blur.toFloat()) }
                                Slider(
                                    value = sliderPos,
                                    onValueChange = { sliderPos = it },
                                    onValueChangeFinished = {
                                        appVm.setGlobalBgBlur(sliderPos.toInt().coerceIn(8, 80))
                                    },
                                    valueRange = 8f..80f,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    cropSource?.let { src ->
        ImageCropperDialog(
            path = src,
            frameAspect = screenAspect,
            onConfirm = { bmp ->
                cropSource = null
                saving = true
                Thread {
                    try {
                        val f = File(context.filesDir, "global_bg.jpg")
                        f.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            saving = false
                            appVm.setGlobalBgPath(f.absolutePath)
                            if (!appVm.globalBgEnabled.value) appVm.setGlobalBgEnabled(true)
                            appVm.showToast("全局背景已设置")
                        }
                    } catch (_: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            saving = false
                            appVm.showToast("设置失败")
                        }
                    }
                }.start()
            },
            onCancel = { cropSource = null },
        )
    }
}
