package top.newblock.fcl.activity

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.mio.JavaManager
import com.mio.manager.RendererManager
import com.mio.util.ImageUtil
import top.newblock.fcl.R
import top.newblock.fcl.fragment.EulaFragment
import top.newblock.fcl.fragment.RuntimeFragment
import top.newblock.fcl.setting.ConfigHolder
import top.newblock.fcl.util.AndroidUtils
import top.newblock.fcl.util.RuntimeUtils
import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fclcore.util.Logging
import com.tungsten.fclcore.util.io.FileUtils
import com.tungsten.fcllibrary.component.FCLActivity
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog
import com.tungsten.fcllibrary.component.theme.ThemeEngine
import com.tungsten.fcllibrary.util.LocaleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.Locale
import java.util.logging.Level

@SuppressLint("CustomSplashScreen")
class SplashActivity : FCLActivity() {
    var lwjgl: Boolean = false
    var cacio: Boolean = false
    var cacio17: Boolean = false
    var java25: Boolean = false
    var jna: Boolean = false
    private lateinit var sharedPreferences: SharedPreferences
    private var waitingForMobileGlues = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContentView(R.layout.activity_splash)
        sharedPreferences = getSharedPreferences("launcher", MODE_PRIVATE)
        val background = findViewById<ConstraintLayout>(R.id.background)
        ImageUtil.loadInto(
            background, ThemeEngine.getInstance().getTheme().getBackground(this)
        )
        if (sharedPreferences.getBoolean("isAgree", false)) {
            checkPermission()
        } else {
            FCLAlertDialog.Builder(this).apply {
                setCancelable(false)
                setAlertLevel(FCLAlertDialog.AlertLevel.ALERT)
                setMessage(getString(R.string.splash_agreement))
                setPositiveButton {
                    sharedPreferences.edit { putBoolean("isAgree", true) }
                    checkPermission()
                }
                setNegativeButton(getString(top.newblock.fcl.R.string.crash_reporter_close)) { finish() }
                create().show()
            }
        }
    }

    private fun checkPermission() {
        if (hasPermission()) {
            init()
            return
        }
        FCLAlertDialog.Builder(this).apply {
            setCancelable(false)
            setAlertLevel(FCLAlertDialog.AlertLevel.ALERT)
            setMessage(getString(R.string.splash_permission_msg))
            setPositiveButton { requestPermission() }
            setNegativeButton { finish() }
            create().show()
        }
    }

    private fun init() {
        lifecycleScope.launch {
            async(Dispatchers.IO) {
                FCLPath.loadPaths(this@SplashActivity)
                Logging.start(Paths.get(FCLPath.LOG_DIR))
                initState()
            }.await()
            if (lwjgl && cacio && cacio17 && java25 && jna) {
                enterLauncher()
            } else {
                start()
            }
        }
    }

    fun start() {
        if (sharedPreferences.getBoolean("isFirstLaunch", true)) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.frag_start_anim, R.anim.frag_stop_anim)
                .replace(R.id.fragment, EulaFragment::class.java, null).commit()
        } else {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.frag_start_anim, R.anim.frag_stop_anim)
                .replace(R.id.fragment, RuntimeFragment::class.java, null).commit()
        }
    }


    fun enterLauncher() {
        if (!isMobileGluesInstalled() && !waitingForMobileGlues) {
            promptInstallMobileGlues()
            return
        }
        doEnterLauncher()
    }

    private fun promptInstallMobileGlues() {
        FCLAlertDialog.Builder(this).apply {
            setCancelable(false)
            setAlertLevel(FCLAlertDialog.AlertLevel.ALERT)
            setMessage("必须安装 MobileGlues 渲染器才能启动游戏")
            setPositiveButton {
                if (installMobileGlues()) {
                    waitingForMobileGlues = true
                } else {
                    finish()
                }
            }
            setNegativeButton { finish() }
            create().show()
        }
    }

    private fun doEnterLauncher() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                RendererManager.init(this@SplashActivity)
                RendererManager.refresh(this@SplashActivity)
                JavaManager.init()
                runCatching { ConfigHolder.init() }.exceptionOrNull()?.let {
                    Logging.LOG.log(Level.WARNING, it.message)
                }
                if (System.currentTimeMillis() - sharedPreferences.getLong(
                        "clear_cache", 0L
                    ) >= 3 * 1000 * 60 * 60 * 24
                ) {
                    FileUtils.cleanDirectoryQuietly(File(FCLPath.CACHE_DIR).getParentFile())
                    sharedPreferences.edit {
                        putLong("clear_cache", System.currentTimeMillis())
                    }
                }
            }
            startActivity(
                handleModpack(Intent(this@SplashActivity, MainActivity::class.java)),
                ActivityOptionsCompat.makeCustomAnimation(this@SplashActivity, 0, 0).toBundle()
            )
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (waitingForMobileGlues) {
            waitingForMobileGlues = false
            doEnterLauncher()
        }
    }

    private fun isMobileGluesInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("com.fcl.plugin.mobileglues", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun installMobileGlues(): Boolean {
        val apkFile = File(FCLPath.CACHE_DIR, "mobileglues.apk")
        if (!apkFile.exists()) {
            try {
                assets.open("plugins/mobileglues.apk").use { input ->
                    apkFile.parentFile?.mkdirs()
                    apkFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                Logging.LOG.log(Level.WARNING, "Failed to copy MobileGlues apk", e)
                return false
            }
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val uri = FileProvider.getUriForFile(this, getString(R.string.file_browser_provider), apkFile)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            startActivity(intent)
            true
        } catch (e: Exception) {
            Logging.LOG.log(Level.WARNING, "Failed to install MobileGlues", e)
            false
        }
    }

    private fun handleModpack(newIntent: Intent): Intent {
        val intent = intent
        val action = intent.action
        val data = intent.data

        if (Intent.ACTION_VIEW == action && data != null) {
            try {
                val fileName = AndroidUtils.getFileName(this, data) ?: "modpack"
                val cacheFile = File(cacheDir, fileName)
                contentResolver.openInputStream(data)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                newIntent.putExtra("modpack_cache_path", cacheFile.absolutePath)
            } catch (e: Exception) {
                Logging.LOG.log(
                    Level.WARNING, "Failed to handle modpack intent: ${e.message}"
                )
            }
        }
        return newIntent
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:$packageName".toUri()
                    startActivityForResult(this) {
                        checkPermission()
                    }
                }
            } catch (_: Exception) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) {
                    checkPermission()
                }
            }
        } else {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this, permission.WRITE_EXTERNAL_STORAGE
                ) || !ActivityCompat.shouldShowRequestPermissionRationale(
                    this, permission.READ_EXTERNAL_STORAGE
                )
            ) {
                requestPermissions(
                    arrayOf(
                        permission.WRITE_EXTERNAL_STORAGE, permission.READ_EXTERNAL_STORAGE
                    )
                ) {
                    checkPermission()
                }
            } else {
                Toast.makeText(this, R.string.splash_permission_settings_msg, Toast.LENGTH_LONG)
                    .show()
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:$packageName".toUri()
                    startActivityForResult(this) {
                        checkPermission()
                    }
                }
            }
        }
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return ContextCompat.checkSelfPermission(
            this, permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this, permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initState() {
        try {
            lwjgl = RuntimeUtils.isLatest(
                FCLPath.LWJGL_DIR + "/3.3.3",
                "/assets/app_runtime/lwjgl/3.3.3"
            ) && RuntimeUtils.isLatest(
                FCLPath.LWJGL_DIR + "/3.4.1",
                "/assets/app_runtime/lwjgl/3.4.1"
            )
            cacio = RuntimeUtils.isLatest(
                FCLPath.CACIOCAVALLO_8_DIR, "/assets/app_runtime/caciocavallo"
            )
            cacio17 = RuntimeUtils.isLatest(
                FCLPath.CACIOCAVALLO_17_DIR, "/assets/app_runtime/caciocavallo17"
            )
            java25 = RuntimeUtils.isLatest(FCLPath.JAVA_25_PATH, "/assets/app_runtime/java/jre25")
            jna = RuntimeUtils.isLatest(FCLPath.JNA_PATH, "/assets/app_runtime/jna")
            if (!File(FCLPath.JAVA_PATH, "resolv.conf").exists()) {
                if (LocaleUtils.getSystemLocale().displayName != Locale.CHINA.displayName) {
                    FileUtils.writeText(
                        File(FCLPath.JAVA_PATH + "/resolv.conf"), """
     nameserver 1.1.1.1
     nameserver 1.0.0.1
     """.trimIndent()
                    )
                } else {
                    FileUtils.writeText(
                        File(FCLPath.JAVA_PATH + "/resolv.conf"), """
     nameserver 8.8.8.8
     nameserver 8.8.4.4
     """.trimIndent()
                    )
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
