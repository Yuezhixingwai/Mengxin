package com.zhiyin.yandere

import android.app.admin.DeviceAdminReceiver

/**
 * 病娇模式的设备管理器接收器。
 * 仅用于 lockNow()（锁屏），不接管任何其他设备策略。
 * 用户随时可在系统设置里撤销。
 */
class YandereDeviceAdminReceiver : DeviceAdminReceiver()
