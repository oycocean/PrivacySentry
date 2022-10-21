package com.yl.lib.privacy_proxy

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.DhcpInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import androidx.annotation.Keep
import com.yl.lib.privacy_annotation.MethodInvokeOpcode
import com.yl.lib.privacy_annotation.PrivacyClassProxy
import com.yl.lib.privacy_annotation.PrivacyMethodProxy
import com.yl.lib.sentry.hook.PrivacySentry
import com.yl.lib.sentry.hook.cache.CachePrivacyManager
import com.yl.lib.sentry.hook.util.PrivacyLog
import com.yl.lib.sentry.hook.util.PrivacyProxyUtil
import com.yl.lib.sentry.hook.util.PrivacyProxyUtil.Util.doFilePrinter
import com.yl.lib.sentry.hook.util.PrivacyUtil
import java.io.File
import java.net.NetworkInterface

/**
 * @author yulun
 * @since 2021-12-22 14:23
 * 大部分敏感api拦截代理
 */
@Keep
open class PrivacyProxyCall {

    // kotlin里实际解析的是这个PrivacyProxyCall$Proxy 内部类
    @PrivacyClassProxy
    @Keep
    object Proxy {
        // 这个方法的注册放在了PrivacyProxyCall2中，提供了一个java注册的例子
        @PrivacyMethodProxy(
            originalClass = ActivityManager::class,
            originalMethod = "getRunningTasks",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getRunningTasks(
            manager: ActivityManager,
            maxNum: Int
        ): List<ActivityManager.RunningTaskInfo?>? {
            doFilePrinter("getRunningTasks", methodDocumentDesc = "当前运行中的任务")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return emptyList()
            }
            return manager.getRunningTasks(maxNum)
        }

        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = ActivityManager::class,
            originalMethod = "getRecentTasks",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getRecentTasks(
            manager: ActivityManager,
            maxNum: Int,
            flags: Int
        ): List<ActivityManager.RecentTaskInfo>? {
            doFilePrinter("getRecentTasks", methodDocumentDesc = "最近运行中的任务")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return emptyList()
            }
            return manager.getRecentTasks(maxNum, flags)
        }


        @PrivacyMethodProxy(
            originalClass = ActivityManager::class,
            originalMethod = "getRunningAppProcesses",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getRunningAppProcesses(manager: ActivityManager): List<ActivityManager.RunningAppProcessInfo> {
            doFilePrinter("getRunningAppProcesses", methodDocumentDesc = "当前运行中的进程")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return emptyList()
            }

            var appProcess: List<ActivityManager.RunningAppProcessInfo> = emptyList()
            try {
                // 线上三星11和12的机子 有上报，量不大
                appProcess = manager.runningAppProcesses
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return appProcess
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "getInstalledPackages",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getInstalledPackages(manager: PackageManager, flags: Int): List<PackageInfo> {
            doFilePrinter("getInstalledPackages", methodDocumentDesc = "安装包-getInstalledPackages")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return emptyList()
            }
            return manager.getInstalledPackages(flags)
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "getInstalledPackagesAsUser",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getInstalledPackagesAsUser(
            manager: PackageManager,
            flags: Int,
            userId: Int
        ): List<PackageInfo> {
            doFilePrinter(
                "getInstalledPackagesAsUser",
                methodDocumentDesc = "安装包-getInstalledPackagesAsUser"
            )
            return getInstalledPackages(manager, flags);
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "getInstalledApplications",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getInstalledApplications(manager: PackageManager, flags: Int): List<ApplicationInfo> {
            doFilePrinter(
                "getInstalledApplications",
                methodDocumentDesc = "安装包-getInstalledApplications"
            )
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return emptyList()
            }
            return manager.getInstalledApplications(flags)
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "getInstalledApplicationsAsUser",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getInstalledApplicationsAsUser(
            manager: PackageManager, flags: Int,
            userId: Int
        ): List<ApplicationInfo> {
            doFilePrinter(
                "getInstalledApplicationsAsUser",
                methodDocumentDesc = "安装包-getInstalledApplicationsAsUser"
            )
            return getInstalledApplications(manager, flags);
        }


        // 这个方法比较特殊，是否合规完全取决于intent参数
        // 如果指定了自己的包名，那可以认为是合规的，因为是查自己APP的AC
        // 如果没有指定包名，那就是查询了其他APP的Ac，这不合规
        // 思考，直接在SDK里拦截肯定不合适，对于业务方来说太黑盒了，如果触发bug开发会崩溃的，所以我们只打日志为业务方提供信息
        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "queryIntentActivities",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun queryIntentActivities(
            manager: PackageManager,
            intent: Intent,
            flags: Int
        ): List<ResolveInfo> {
            var paramBuilder = StringBuilder()
            var legal = true
            intent?.also {
                intent?.categories?.also {
                    paramBuilder.append("-categories:").append(it.toString()).append("\n")
                }
                intent?.`package`?.also {
                    paramBuilder.append("-packageName:").append(it).append("\n")
                }
                intent?.data?.also {
                    paramBuilder.append("-data:").append(it.toString()).append("\n")
                }
                intent?.component?.packageName?.also {
                    paramBuilder.append("-packageName:").append(it).append("\n")
                }
            }

            if (paramBuilder.isEmpty()) {
                legal = false
            }

            //不指定包名，我们认为这个查询不合法
            if (!paramBuilder.contains("packageName")) {
                legal = false
            }
            paramBuilder.append("-合法查询:${legal}").append("\n")
            doFilePrinter(
                "queryIntentActivities",
                methodDocumentDesc = "读安装列表-queryIntentActivities${paramBuilder?.toString()}"
            )
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return emptyList()
            }
            return manager.queryIntentActivities(intent, flags)
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "queryIntentActivityOptions",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun queryIntentActivityOptions(
            manager: PackageManager,
            caller: ComponentName?,
            specifics: Array<Intent?>?,
            intent: Intent,
            flags: Int
        ): List<ResolveInfo> {
            doFilePrinter(
                "queryIntentActivityOptions",
                methodDocumentDesc = "读安装列表-queryIntentActivityOptions"
            )
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return emptyList()
            }
            return manager.queryIntentActivityOptions(caller, specifics, intent, flags)
        }


        /**
         * 基站信息，需要开启定位
         */
        @JvmStatic
        @SuppressLint("MissingPermission")
        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getAllCellInfo",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getAllCellInfo(manager: TelephonyManager): List<CellInfo>? {
            doFilePrinter("getAllCellInfo", methodDocumentDesc = "定位-基站信息")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return emptyList()
            }
            return manager.getAllCellInfo()
        }

        @PrivacyMethodProxy(
            originalClass = ClipboardManager::class,
            originalMethod = "getPrimaryClip",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getPrimaryClip(manager: ClipboardManager): ClipData? {
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return ClipData.newPlainText("Label", "")
            }

            if (!PrivacySentry.Privacy.isReadClipboardEnable()) {
                doFilePrinter("getPrimaryClip", "读取系统剪贴板关闭")
                return ClipData.newPlainText("Label", "")
            }

            doFilePrinter("getPrimaryClip", "剪贴板内容-getPrimaryClip")
            return manager.primaryClip
        }

        @PrivacyMethodProxy(
            originalClass = ClipboardManager::class,
            originalMethod = "getPrimaryClipDescription",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getPrimaryClipDescription(manager: ClipboardManager): ClipDescription? {
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return ClipDescription("", arrayOf(MIMETYPE_TEXT_PLAIN))
            }

            if (!PrivacySentry.Privacy.isReadClipboardEnable()) {
                doFilePrinter("getPrimaryClipDescription", "读取系统剪贴板关闭")
                return ClipDescription("", arrayOf(MIMETYPE_TEXT_PLAIN))
            }

            doFilePrinter("getPrimaryClipDescription", "剪贴板内容-getPrimaryClipDescription")
            return manager.primaryClipDescription
        }

        @PrivacyMethodProxy(
            originalClass = ClipboardManager::class,
            originalMethod = "getText",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getText(manager: ClipboardManager): CharSequence? {

            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return ""
            }

            if (!PrivacySentry.Privacy.isReadClipboardEnable()) {
                doFilePrinter("getText", "读取系统剪贴板关闭")
                return ""
            }
            doFilePrinter("getText", "剪贴板内容-getText")
            return manager.text
        }

        @PrivacyMethodProxy(
            originalClass = ClipboardManager::class,
            originalMethod = "setPrimaryClip",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun setPrimaryClip(manager: ClipboardManager, clip: ClipData) {
            doFilePrinter("setPrimaryClip", "设置剪贴板内容-setPrimaryClip")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return
            }
            manager.setPrimaryClip(clip)
        }

        @PrivacyMethodProxy(
            originalClass = ClipboardManager::class,
            originalMethod = "setText",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun setText(manager: ClipboardManager, clip: CharSequence) {
            doFilePrinter("setText", "设置剪贴板内容-setText")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return
            }
            manager.text = clip
        }

        /**
         * WIFI的SSID
         */
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiInfo::class,
            originalMethod = "getSSID",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getSSID(manager: WifiInfo): String? {
            doFilePrinter("getSSID", "SSID")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return ""
            }
            return manager.ssid
        }

        /**
         * WIFI的SSID
         */
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiInfo::class,
            originalMethod = "getBSSID",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getBSSID(manager: WifiInfo): String? {
            doFilePrinter("getBSSID", "BSSID")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return ""
            }
            return manager.bssid
        }

        /**
         * WIFI扫描结果
         */
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiManager::class,
            originalMethod = "getScanResults",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getScanResults(manager: WifiManager): List<ScanResult>? {
            doFilePrinter("getScanResults", "WIFI扫描结果")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return emptyList()
            }
            return manager.getScanResults()
        }


        /**
         * DHCP信息
         */
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiManager::class,
            originalMethod = "getDhcpInfo",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getDhcpInfo(manager: WifiManager): DhcpInfo? {
            doFilePrinter("getDhcpInfo", "DHCP地址")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return null
            }
            return manager.getDhcpInfo()
        }

        /**
         * DHCP信息
         */
        @SuppressLint("MissingPermission")
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiManager::class,
            originalMethod = "getConfiguredNetworks",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getConfiguredNetworks(manager: WifiManager): List<WifiConfiguration>? {
            doFilePrinter("getConfiguredNetworks", "前台用户配置的所有网络的列表")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return emptyList()
            }
            return manager.getConfiguredNetworks()
        }


        /**
         * 位置信息
         */
        @JvmStatic
        @SuppressLint("MissingPermission")
        @PrivacyMethodProxy(
            originalClass = LocationManager::class,
            originalMethod = "getLastKnownLocation",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getLastKnownLocation(
            manager: LocationManager, provider: String
        ): Location? {
            var key = "getLastKnownLocation"
            doFilePrinter("getLastKnownLocation", "上一次的位置信息")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                // 这里直接写空可能有风险
                return null
            }

            var locationStr = CachePrivacyManager.manager.loadWithTimeCache(
                key,
                "上一次的位置信息",
                ""
            ) { PrivacyUtil.Util.formatLocation(manager.getLastKnownLocation(provider)) }

            var location: Location? = null
            locationStr.also {
                location = PrivacyUtil.Util.formatLocation(it)
            }
            return location
        }


        @SuppressLint("MissingPermission")
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = LocationManager::class,
            originalMethod = "requestLocationUpdates",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun requestLocationUpdates(
            manager: LocationManager, provider: String, minTime: Long, minDistance: Float,
            listener: LocationListener
        ) {
            doFilePrinter("requestLocationUpdates", "监视精细行动轨迹")
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return
            }
            manager.requestLocationUpdates(provider, minTime, minDistance, listener)
        }


        var objectImeiLock = Object()
        var objectImsiLock = Object()
        var objectMacLock = Object()
        var objectHardMacLock = Object()
        var objectSNLock = Object()
        var objectAndroidIdLock = Object()
        var objectExternalStorageDirectoryLock = Object()

        var objectMeidLock = Object()

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getMeid",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getMeid(manager: TelephonyManager): String? {
            var key = "meid"
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(key, "移动设备标识符-getMeid()", bVisitorModel = true)
                return ""
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return ""
            }

            if (!PrivacyProxyUtil.Util.checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                doFilePrinter("getMeid", methodDocumentDesc = "移动设备标识符-getMeid()-无权限")
                return ""
            }

            synchronized(objectMeidLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "移动设备标识符-getMeid()",
                    ""
                ) { manager.meid }
            }
        }

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getMeid",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getMeid(manager: TelephonyManager, index: Int): String? {
            var key = "meid"
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(key, "移动设备标识符-getMeid()", bVisitorModel = true)
                return ""
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return ""
            }
            if (!PrivacyProxyUtil.Util.checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                doFilePrinter("getMeid", methodDocumentDesc = "移动设备标识符-getMeid()-无权限")
                return ""
            }
            synchronized(objectMeidLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "移动设备标识符-getMeid(I)",
                    ""
                ) { manager.meid }
            }
        }

        var objectDeviceIdLock = Object()

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getDeviceId",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getDeviceId(manager: TelephonyManager): String? {
            var key = "TelephonyManager-getDeviceId"
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(key, "IMEI-getDeviceId()", bVisitorModel = true)
                return ""
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return ""
            }

            if (!PrivacyProxyUtil.Util.checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                doFilePrinter(key, "IMEI-getDeviceId()-无权限")
                return ""
            }
            synchronized(objectDeviceIdLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "IMEI-getDeviceId()",
                    ""
                ) { manager.getDeviceId() }
            }
        }

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getDeviceId",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getDeviceId(manager: TelephonyManager, index: Int): String? {
            var key = "TelephonyManager-getDeviceId-$index"

            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(
                    key,
                    "IMEI-getDeviceId(I)",
                    bVisitorModel = true
                )
                return ""
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return ""
            }

            if (!PrivacyProxyUtil.Util.checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                doFilePrinter(key, "IMEI-getDeviceId()-无权限")
                return ""
            }
            synchronized(objectDeviceIdLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "IMEI-getDeviceId(I)",
                    ""
                ) { manager.getDeviceId(index) }
            }
        }

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getSubscriberId",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getSubscriberId(manager: TelephonyManager): String? {
            var key = "TelephonyManager-getSubscriberId"

            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(
                    key,
                    "IMSI-getSubscriberId(I)",
                    bVisitorModel = true
                )
                return ""
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return ""
            }

            if (!PrivacyProxyUtil.Util.checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                doFilePrinter(key, "IMSI-getSubscriberId(I)-无权限")
                return ""
            }

            synchronized(objectImsiLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "IMSI-getSubscriberId()",
                    ""
                ) { manager.subscriberId }
            }
        }

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getSubscriberId",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getSubscriberId(manager: TelephonyManager, index: Int): String? {
            return getSubscriberId(manager)
        }

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getImei",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getImei(manager: TelephonyManager): String? {
            var key = "TelephonyManager-getImei"

            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(key, "IMEI-getImei()", bVisitorModel = true)
                return ""
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return ""
            }

            if (!PrivacyProxyUtil.Util.checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                doFilePrinter(key, "IMEI-getImei()-无权限")
                return ""
            }

            synchronized(objectImeiLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "IMEI-getImei()",
                    ""
                ) { manager.imei }
            }
        }

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getImei",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getImei(manager: TelephonyManager, index: Int): String? {
            var key = "TelephonyManager-getImei-$index"
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(key, "设备id-getImei(I)", bVisitorModel = true)
                return ""
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return ""
            }

            if (!PrivacyProxyUtil.Util.checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                doFilePrinter(key, "设备id-getImei(I)-无权限")
                return ""
            }

            synchronized(objectImeiLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "IMEI-getImei(I)",
                    ""
                ) { manager.getImei(index) }
            }
        }

        var objectSimLock = Object()

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getSimSerialNumber",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getSimSerialNumber(manager: TelephonyManager): String? {
            var key = "TelephonyManager-getSimSerialNumber"
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(
                    key,
                    "SIM卡-getSimSerialNumber()",
                    bVisitorModel = true
                )
                return ""
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return ""
            }

            if (!PrivacyProxyUtil.Util.checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                doFilePrinter(key, "SIM卡-getSimSerialNumber()-无权限")
                return ""
            }
            synchronized(objectSimLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "SIM卡-getSimSerialNumber()",
                    ""
                ) { manager.getSimSerialNumber() }
            }
        }

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getSimSerialNumber",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getSimSerialNumber(manager: TelephonyManager, index: Int): String? {
            return getSimSerialNumber(manager)
        }

        var objectPhoneNumberLock = Object()

        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getLine1Number",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @SuppressLint("MissingPermission")
        @JvmStatic
        fun getLine1Number(manager: TelephonyManager): String? {

            var key = "TelephonyManager-getLine1Number"

            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(key, "手机号-getLine1Number", bVisitorModel = true)
                return ""
            }
            synchronized(objectPhoneNumberLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "手机号-getLine1Number",
                    ""
                ) { manager.line1Number }
            }
        }


        @PrivacyMethodProxy(
            originalClass = WifiInfo::class,
            originalMethod = "getMacAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getMacAddress(manager: WifiInfo): String? {
            var key = "WifiInfo-getMacAddress"

            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(
                    key,
                    "mac地址-getMacAddress",
                    bVisitorModel = true
                )
                return ""
            }

            synchronized(objectMacLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "mac地址-getMacAddress",
                    ""
                ) { manager.getMacAddress() }
            }
        }


        @PrivacyMethodProxy(
            originalClass = NetworkInterface::class,
            originalMethod = "getHardwareAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getHardwareAddress(manager: NetworkInterface): ByteArray? {
            var key = "NetworkInterface-getHardwareAddress"

            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(
                    key,
                    "mac地址-getHardwareAddress",
                    bVisitorModel = true
                )
                return ByteArray(1)
            }
            synchronized(objectHardMacLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "mac地址-getHardwareAddress",
                    ""
                ) { manager.hardwareAddress.toString() }.toByteArray()
            }
        }

        var objectBluetoothLock = Object()

        @PrivacyMethodProxy(
            originalClass = BluetoothAdapter::class,
            originalMethod = "getAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getAddress(manager: BluetoothAdapter): String? {
            var key = "BluetoothAdapter-getAddress"

            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(key, "蓝牙地址-getAddress", bVisitorModel = true)
                return ""
            }
            synchronized(objectBluetoothLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "蓝牙地址-getAddress",
                    ""
                ) { manager.address }
            }
        }


        @PrivacyMethodProxy(
            originalClass = Settings.Secure::class,
            originalMethod = "getString",
            originalOpcode = MethodInvokeOpcode.INVOKESTATIC
        )
        @JvmStatic
        fun getString(contentResolver: ContentResolver?, type: String?): String? {
            var key = "Secure-getString-$type"
            if (!"android_id".equals(type)) {
                return Settings.Secure.getString(
                    contentResolver,
                    type
                )
            }
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter(
                    "getString",
                    "系统信息",
                    args = type,
                    bVisitorModel = true
                )
                return ""
            }
            synchronized(objectAndroidIdLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "getString-系统信息",
                    ""
                ) {
                    Settings.Secure.getString(
                        contentResolver,
                        type
                    )
                }
            }
        }


        @PrivacyMethodProxy(
            originalClass = Settings.System::class,
            originalMethod = "getString",
            originalOpcode = MethodInvokeOpcode.INVOKESTATIC
        )
        @JvmStatic
        fun getStringSystem(contentResolver: ContentResolver?, type: String?): String? {
            return getString(contentResolver, type)
        }

        @PrivacyMethodProxy(
            originalClass = android.os.Build::class,
            originalMethod = "getSerial",
            originalOpcode = MethodInvokeOpcode.INVOKESTATIC
        )
        @JvmStatic
        fun getSerial(): String? {
            var result = ""
            var key = "getSerial"
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter("getSerial", "Serial", bVisitorModel = true)
                return ""
            }
            synchronized(objectSNLock) {
                return CachePrivacyManager.manager.loadWithDiskCache(
                    key,
                    "getSerial",
                    ""
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Build.getSerial()
                    } else {
                        Build.SERIAL
                    }
                }
            }
        }

        @PrivacyMethodProxy(
            originalClass = android.os.Environment::class,
            originalMethod = "getExternalStorageDirectory",
            originalOpcode = MethodInvokeOpcode.INVOKESTATIC
        )
        @JvmStatic
        fun getExternalStorageDirectory(): File? {
            var result: File? = null
            var key = "externalStorageDirectory"
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                doFilePrinter("getExternalStorageDirectory", key, bVisitorModel = true)
            }
            synchronized(objectExternalStorageDirectoryLock) {
                result = CachePrivacyManager.manager.loadWithMemoryCache<File>(
                    key,
                    "getExternalStorageDirectory",
                    File("")
                ) {
                    Environment.getExternalStorageDirectory()
                }
            }
            return result
        }

        // 拦截获取系统设备，简直离谱，这个也不能重复获取
        @JvmStatic
        fun getBrand(): String? {
            PrivacyLog.i("getBrand")
            var key = "getBrand"
            return CachePrivacyManager.manager.loadWithMemoryCache(
                key,
                "getBrand",
                ""
            ) {
                PrivacyLog.i("getBrand Value")
                Build.BRAND
            }
        }
    }
}