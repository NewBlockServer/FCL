package top.newblock.fcl.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.forEach
import androidx.core.view.postDelayed
import androidx.lifecycle.lifecycleScope
import com.mio.manager.RendererManager
import com.mio.ui.dialog.RendererSelectDialog
import com.mio.util.AnimUtil
import com.mio.util.AnimUtil.Companion.interpolator
import com.mio.util.AnimUtil.Companion.startAfter
import com.mio.util.DisplayUtil
import com.mio.util.GuideUtil
import com.mio.util.GuideUtil.Companion.guideTarget
import com.mio.util.ImageUtil
import com.mio.util.showWarningDialog
import top.newblock.fcl.R
import top.newblock.fcl.databinding.ActivityMainBinding
import top.newblock.fcl.game.JarExecutorHelper
import top.newblock.fcl.game.TexturesLoader
import top.newblock.fcl.setting.Accounts
import top.newblock.fcl.setting.ConfigHolder
import top.newblock.fcl.setting.Controllers
import top.newblock.fcl.setting.Profile
import top.newblock.fcl.setting.Profiles
import top.newblock.fcl.ui.PageManager
import top.newblock.fcl.ui.UIManager
import top.newblock.fcl.ui.download.modpack.LocalModpackPage
import top.newblock.fcl.ui.version.Versions
import top.newblock.fcl.upgrade.UpdateChecker
import top.newblock.fcl.util.AndroidUtils
import top.newblock.fcl.util.FXUtils
import top.newblock.fcl.util.WeakListenerHolder
import com.tungsten.fclauncher.plugins.DriverPlugin
import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fclcore.auth.Account
import com.tungsten.fclcore.auth.authlibinjector.AuthlibInjectorAccount
import com.tungsten.fclcore.auth.authlibinjector.AuthlibInjectorServer
import com.tungsten.fclcore.auth.yggdrasil.TextureModel
import com.tungsten.fclcore.download.LibraryAnalyzer
import com.tungsten.fclcore.download.LibraryAnalyzer.LibraryType
import com.tungsten.fclcore.fakefx.beans.binding.Bindings
import com.tungsten.fclcore.fakefx.beans.property.IntegerProperty
import com.tungsten.fclcore.fakefx.beans.property.IntegerPropertyBase
import com.tungsten.fclcore.fakefx.beans.property.ObjectProperty
import com.tungsten.fclcore.fakefx.beans.property.SimpleObjectProperty
import com.tungsten.fclcore.fakefx.beans.value.ObservableValue
import com.tungsten.fclcore.mod.RemoteMod
import com.tungsten.fclcore.mod.RemoteMod.IMod
import com.tungsten.fclcore.mod.RemoteModRepository
import com.tungsten.fclcore.util.Logging.LOG
import com.tungsten.fclcore.util.fakefx.BindingMapping
import com.tungsten.fcllibrary.component.FCLActivity
import com.tungsten.fcllibrary.component.dialog.EditDialog
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog
import com.tungsten.fcllibrary.component.theme.ThemeEngine
import com.tungsten.fcllibrary.component.view.FCLMenuView
import com.tungsten.fcllibrary.component.view.FCLMenuView.OnSelectListener
import com.tungsten.fcllibrary.util.ConvertUtils
import com.tungsten.fcllibrary.ui.ProgressDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.stream.Stream
import com.tungsten.fclcore.mod.Modpack
import com.tungsten.fclcore.task.FileDownloadTask
import com.tungsten.fclcore.task.Schedulers
import com.tungsten.fclcore.task.Task
import com.tungsten.fclcore.task.TaskExecutor
import com.tungsten.fclcore.task.TaskListener
import com.tungsten.fclcore.util.io.CompressingUtils
import com.tungsten.fclcore.util.io.HttpRequest
import com.tungsten.fclcore.util.io.NetworkUtils
import top.newblock.fcl.game.ModpackHelper
import top.newblock.fcl.ui.TaskDialog
import top.newblock.fcl.util.TaskCancellationAction
import kotlin.system.exitProcess

class MainActivity : FCLActivity(), OnSelectListener, View.OnClickListener {
    companion object {
        private const val MODPACK_URL = "http://example.com/FCL/v1/modpack.json"

        private lateinit var instance: WeakReference<MainActivity>

        @JvmStatic
        fun getInstance(): MainActivity {
            return instance.get()!!
        }
    }

    lateinit var binding: ActivityMainBinding
    private var _uiManager: UIManager? = null
    lateinit var uiManager: UIManager
    private lateinit var currentAccount: ObjectProperty<Account?>
    private val holder = WeakListenerHolder()
    private lateinit var profile: Profile
    private var isLaunchingModpack = false
    private lateinit var theme: IntegerProperty
    private lateinit var theme2: IntegerProperty
    private lateinit var theme2Dark: IntegerProperty
    var isVersionLoading = false
    private var modpackHandled = false
    lateinit var permissionResultLauncher: ActivityResultLauncher<String>
    private lateinit var sharedPreferences: SharedPreferences
    var mediaPlayer: MediaPlayer? = null
    private var videoPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modpackHandled = savedInstanceState?.getBoolean("modpack_handled") ?: false
        instance = WeakReference(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        sharedPreferences = getSharedPreferences("launcher", MODE_PRIVATE)
        setContentView(binding.root)
        ImageUtil.loadInto(
            binding.background,
            ThemeEngine.getInstance().getTheme().getBackground(this)
        )

        RemoteMod.registerEmptyRemoteMod(
            RemoteMod(
                "",
                "",
                getString(R.string.mods_broken_dependency_title),
                getString(R.string.mods_broken_dependency_desc),
                ArrayList(),
                "",
                "",
                object : IMod {
                    @Throws(IOException::class)
                    override fun loadDependencies(modRepository: RemoteModRepository): List<RemoteMod> {
                        throw IOException()
                    }

                    @Throws(IOException::class)
                    override fun loadVersions(modRepository: RemoteModRepository): Stream<RemoteMod.Version> {
                        throw IOException()
                    }

                    override fun loadScreenshots(modRepository: RemoteModRepository): MutableList<RemoteMod.Screenshot> {
                        throw IOException()
                    }
                },
                114514,
                ""
            )
        )

        if (!ConfigHolder.isInit()) {
            try {
                ConfigHolder.init()
                //当强制关闭进程时，不会经过SplashActivity，此时需要重新初始化
                RendererManager.init(this@MainActivity)
            } catch (e: IOException) {
                LOG.log(Level.WARNING, e.message)
            }
        }

        binding.apply {
            initBackground()
            uiLayout.post {
                ThemeEngine.getInstance().registerEvent(leftMenu) {
                    leftMenu.background = GradientDrawable().apply {
                        setColor(ThemeEngine.getInstance().getTheme().color)
                        shape = GradientDrawable.RECTANGLE
                        ConvertUtils.dip2px(this@MainActivity, 8f).toFloat().apply {
                            cornerRadii = floatArrayOf(0f, 0f, this, this, this, this, 0f, 0f)
                        }
                    }
                }

                account.setOnClickListener(this@MainActivity)
                version.setOnClickListener(this@MainActivity)
                goSetting.setOnClickListener(this@MainActivity)
                start.setOnClickListener(this@MainActivity)
                start.setOnLongClickListener { view ->
                    RendererSelectDialog(this@MainActivity, false) {
                        onClick(view)
                    }.show()
                    true
                }
                jar.setOnClickListener(this@MainActivity)
                jar.setOnLongClickListener {
                    EditDialog(this@MainActivity) {
                        JarExecutorHelper.exec(
                            this@MainActivity,
                            null,
                            JarExecutorHelper.getJava(null),
                            it
                        )
                    }.apply {
                        setTitle(R.string.jar_execute_custom_args)
                        binding.editText.hint = "-jar xxx"
                        binding.editText.setLines(1)
                        binding.editText.maxLines = 1
                    }.show()
                    true
                }

                uiManager = UIManager(this@MainActivity, uiLayout)
                _uiManager = uiManager
                uiManager.registerDefaultBackEvent {
                    if (uiManager.currentUI === uiManager.mainUI) {
                        val i = Intent(Intent.ACTION_MAIN)
                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        i.addCategory(Intent.CATEGORY_HOME)
                        startActivity(i)
                        exitProcess(0)
                    } else {
                        home.isSelected = true
                    }
                }
                uiManager.init()
                home.setOnSelectListener(this@MainActivity)
                manage.setOnSelectListener(this@MainActivity)
                controller.setOnSelectListener(this@MainActivity)
                setting.setOnSelectListener(this@MainActivity)
                home.setSelected(true)
                home.setOnLongClickListener {
                    shareLog()
                    true
                }
                back.setOnClickListener(this@MainActivity)
                back.setOnLongClickListener {
                    startActivity(Intent(this@MainActivity, ShellActivity::class.java))
                    true
                }
                UpdateChecker.getInstance().checkAuto(this@MainActivity).start()
                if (!checkNotificationPermission() && getSharedPreferences(
                        "launcher",
                        MODE_PRIVATE
                    ).getBoolean("check_notification_permission", true)
                ) {
                    getSharedPreferences("launcher", MODE_PRIVATE).edit {
                        putBoolean("check_notification_permission", false)
                    }
                    FCLAlertDialog.Builder(this@MainActivity)
                        .setMessage(getString(R.string.notification_permission))
                        .setPositiveButton {
                            requestNotificationPermission()
                        }
                        .setNegativeButton {}
                        .create()
                        .show()
                }
                if (!modpackHandled) {
                    handleModpack(intent)
                }
                setupAccountDisplay()
                setupVersionDisplay()
                playAnim()
                uiLayout.postDelayed(1500) {
                    GuideUtil.show(
                        activity = this@MainActivity,
                        GuideUtil.TAG_GUIDE_THEME_2 to setting.guideTarget(title = getString(R.string.guide_theme2)),
                        GuideUtil.TAG_GUIDE_SHARE_LOG to home.guideTarget(title = getString(R.string.guide_share_log))
                    )
                }
            }
        }
        permissionResultLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            }
        setupLiveBackground()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            _uiManager?.onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("modpack_handled", modpackHandled)
    }

    override fun onPause() {
        super.onPause()
        _uiManager?.onPause()
        if (shouldPlayVideo() && binding.videoView.isPlaying) {
            videoPosition = binding.videoView.currentPosition
            binding.videoView.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        _uiManager?.onResume()
        if (shouldPlayVideo() && !binding.videoView.isPlaying) {
            binding.videoView.seekTo(videoPosition)
            binding.videoView.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (shouldPlayVideo()) {
            mediaPlayer = null
            binding.videoView.stopPlayback()
        }
    }

    override fun onSelect(view: FCLMenuView) {
        refreshMenuView(view)
        val speed = ThemeEngine.getInstance().getTheme().animationSpeed
        AnimUtil.playRotation(view, speed * 100L, 0f, 360f)
            .interpolator(OvershootInterpolator()).start()
        AnimUtil.playScaleX(view, speed * 100L, 1f, 2f, 1f)
            .start()
        AnimUtil.playScaleY(view, speed * 100L, 1f, 2f, 1f)
            .start()
        binding.apply {
            when (view) {
                home -> {
                    title.setTextWithAnim(getString(R.string.app_name) + " " + getString(R.string.app_version))
                    uiManager.switchUI(uiManager.mainUI)
                }

                manage -> {
                    val version = Profiles.getSelectedProfile().selectedVersion
                    if (version == null) {
                        refreshMenuView(null)
                        title.setTextWithAnim(getString(R.string.version))
                        uiManager.switchUI(uiManager.versionUI)
                    } else {
                        title.setTextWithAnim(getString(R.string.manage))
                        uiManager.manageUI.setVersion(version, Profiles.getSelectedProfile())
                        uiManager.switchUI(uiManager.manageUI)
                    }
                }

                controller -> {
                    title.setTextWithAnim(getString(R.string.controller))
                    uiManager.switchUI(uiManager.controllerUI)
                }

                setting -> {
                    title.setTextWithAnim(getString(R.string.setting))
                    uiManager.switchUI(uiManager.settingUI)
                }
            }
        }
    }

    fun refreshMenuView(view: FCLMenuView?) {
        binding.leftMenu.forEach {
            if (it is FCLMenuView && it != view) {
                it.isSelected = false
            }
        }
    }

    override fun onClick(view: View) {
        binding.apply {
            if (view === account && uiManager.currentUI !== uiManager.accountUI) {
                refreshMenuView(null)
                title.setTextWithAnim(getString(R.string.account))
                uiManager.switchUI(uiManager.accountUI)
            }
            if (view === version && uiManager.currentUI !== uiManager.versionUI) {
                refreshMenuView(null)
                title.setTextWithAnim(getString(R.string.version))
                uiManager.switchUI(uiManager.versionUI)
            }
            if (view === back) {
                uiManager.onBackPressed()
            }
            if (view === jar) {
                if (sharedPreferences.getBoolean("showJarExecutorWarnDialog", true)) {
                    showWarningDialog(this@MainActivity, getString(R.string.jar_executor_warn)) {
                        sharedPreferences.edit {
                            putBoolean("showJarExecutorWarnDialog", false)
                        }
                    }
                    return
                }
                jar.isSelected = false
                JarExecutorHelper.start(this@MainActivity)
            }
            if (view === start) {
                if (!Controllers.isInitialized()) {
                    title.setTextWithAnim(getString(R.string.message_loading_controllers))
                    AnimUtil.playTranslationX(start, 700, 0f, 50f, -50f, 50f, -50f, 0f)
                        .interpolator(OvershootInterpolator()).start()
                    return
                }
                val selectedProfile = Profiles.getSelectedProfile()
                DriverPlugin.selected = runCatching {
                    DriverPlugin.driverList.find {
                        it.driver == selectedProfile.getVersionSetting(selectedProfile.selectedVersion).driver
                    }
                }.getOrNull() ?: DriverPlugin.driverList[0]
                refreshScreenSize()
                DisplayUtil.refreshDisplayMetrics(this@MainActivity)
                launchServerModpack()
            }
            if (view === goSetting) {
                val profile = Profiles.getSelectedProfile()
                if (profile.versionSetting.isGlobal) {
                    setting.isSelected = true
                    uiManager.settingUI.runAfterInit {
                        val tab = uiManager.settingUI.tabLayout.getTabAt(0)
                        uiManager.settingUI.tabLayout.selectTab(tab)
                    }
                } else {
                    manage.isSelected = true
                    uiManager.manageUI.runAfterInit {
                        val tab = uiManager.manageUI.tabLayout.getTabAt(0)
                        uiManager.manageUI.tabLayout.selectTab(tab)
                    }
                }
            }
        }
    }

    private fun setupAccountDisplay() {
        binding.apply {
            currentAccount = object : SimpleObjectProperty<Account?>() {
                override fun invalidated() {
                    val account = get()
                    if (account == null) {
                        accountName.stringProperty().unbind()
                        accountHint.stringProperty().unbind()
                        avatar.imageProperty().unbind()
                        accountName.text = getString(R.string.account_state_no_account)
                        accountHint.text = getString(R.string.account_state_add)
                        avatar.setBackgroundDrawable(
                            TexturesLoader.toAvatar(
                                TexturesLoader.getDefaultSkin(TextureModel.ALEX).image(),
                                ConvertUtils.dip2px(
                                    this@MainActivity, 52f
                                )
                            ).toDrawable(resources)
                        )
                    } else {
                        accountName.stringProperty()
                            .bind(BindingMapping.of(account) { obj: Account -> obj.character })
                        accountHint.stringProperty()
                            .bind(accountSubtitle(this@MainActivity, account))
                        avatar.imageProperty().unbind()
                        avatar.imageProperty().bind(
                            TexturesLoader.avatarBinding(
                                account, ConvertUtils.dip2px(
                                    this@MainActivity, 52f
                                )
                            )
                        )
                    }
                }
            }
            (currentAccount as SimpleObjectProperty<Account?>).bind(Accounts.selectedAccountProperty())
        }
    }

    fun refreshAvatar(account: Account) {
        lifecycleScope.launch {
            if (currentAccount.get() === account) {
                binding.avatar.imageProperty().unbind()
                binding.avatar.imageProperty().bind(
                    TexturesLoader.avatarBinding(
                        currentAccount.get(), ConvertUtils.dip2px(
                            this@MainActivity, 52f
                        )
                    )
                )
            }
        }
    }

    private fun loadVersion(version: String?) {
        isVersionLoading = true
        binding.versionProgress.visibility = View.VISIBLE
        profile = Profiles.getSelectedProfile()
        if (version != null && profile.repository.hasVersion(version)) {
            lifecycleScope.launch(Dispatchers.IO) {
                var game: String? = null
                runCatching {
                    game = profile.repository.getGameVersion(version)
                        .orElse(getString(R.string.message_unknown))
                }
                if (game == null) return@launch
                val libraries = StringBuilder(game)
                val analyzer = LibraryAnalyzer.analyze(
                    profile.repository.getResolvedPreservingPatchesVersion(
                        version
                    ),
                    profile.repository.getGameVersion(version).orElse(null)
                )
                for (mark in analyzer) {
                    val libraryId = mark.libraryId
                    val libraryVersion = mark.libraryVersion
                    if (libraryId == LibraryType.MINECRAFT.patchId) continue
                    if (AndroidUtils.hasStringId(
                            this@MainActivity,
                            "install_installer_" + libraryId.replace("-", "_")
                        )
                    ) {
                        libraries.append(", ").append(
                            AndroidUtils.getLocalizedText(
                                this@MainActivity,
                                "install_installer_" + libraryId.replace("-", "_")
                            )
                        )
                        if (libraryVersion != null) libraries.append(": ").append(
                            libraryVersion.replace(
                                "(?i)$libraryId".toRegex(), ""
                            )
                        )
                    }
                }
                val drawable = profile.repository.getVersionIconImage(version)
                withContext(Dispatchers.Main) {
                    isVersionLoading = false
                    binding.versionProgress.visibility = View.GONE
                    binding.versionName.text = version
                    binding.versionName.isSelected = true
//                    binding.versionHint.text = libraries.toString()
                    binding.icon.setBackgroundDrawable(drawable)
                }
            }
        } else {
            isVersionLoading = false
            binding.versionProgress.visibility = View.GONE
            binding.versionName.text = getString(R.string.version_no_version)
            binding.icon.setBackgroundDrawable(
                AppCompatResources.getDrawable(
                    this,
                    R.drawable.img_grass
                )
            )
        }
    }

    private fun setupVersionDisplay() {
        holder.add(FXUtils.onWeakChangeAndOperate(Profiles.selectedVersionProperty()) { s: String? ->
            lifecycleScope.launch { loadVersion(s) }
        })
    }

    private fun accountSubtitle(context: Context, account: Account): ObservableValue<String> {
        return if (account is AuthlibInjectorAccount) {
            BindingMapping.of(account.server) { obj: AuthlibInjectorServer -> obj.name }
        } else {
            Bindings.createStringBinding({
                Accounts.getLocalizedLoginTypeName(
                    context,
                    Accounts.getAccountFactory(account)
                )
            })
        }
    }

    private fun updateColor() {
        binding.apply {
            start.background = createBackground()
            createBackground().apply {
                version.background = this
                jar.background = this
            }
            version.backgroundTintList =
                ColorStateList.valueOf(ThemeEngine.getInstance().theme.color2).apply {
                    version.backgroundTintList = this
                    jar.backgroundTintList = this
                }
            version.setTextColor(ThemeEngine.getInstance().theme.color2)
            jar.setTextColor(ThemeEngine.getInstance().theme.color2)
        }

    }

    private fun initBackground() {
        theme = object : IntegerPropertyBase() {
            override fun invalidated() {
                get()
                updateColor()
            }

            override fun getBean(): Any {
                return this
            }

            override fun getName(): String {
                return "theme"
            }
        }
        theme2 = object : IntegerPropertyBase() {
            override fun invalidated() {
                get()
                updateColor()
            }

            override fun getBean(): Any {
                return this
            }

            override fun getName(): String {
                return "theme2"
            }
        }
        theme2Dark = object : IntegerPropertyBase() {
            override fun invalidated() {
                get()
                updateColor()
            }

            override fun getBean(): Any {
                return this
            }

            override fun getName(): String {
                return "theme2Dark"
            }
        }
        theme.bind(ThemeEngine.getInstance().theme.colorProperty())
        theme2.bind(ThemeEngine.getInstance().theme.color2Property())
        theme2Dark.bind(ThemeEngine.getInstance().theme.color2DarkProperty())
    }

    private fun createBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ConvertUtils.dip2px(this@MainActivity, 8f).toFloat()
            setStroke(
                ConvertUtils.dip2px(this@MainActivity, 1f),
                ThemeEngine.getInstance().theme.color2
            )
        }
    }

    private fun playAnim() {
        binding.apply {
            val speed = ThemeEngine.getInstance().theme.animationSpeed
            AnimUtil.playTranslationX(
                listOf(leftMenu),
                speed * 100L,
                -100f,
                0f
            ).forEach {
                it.interpolator(BounceInterpolator()).start()
            }
            AnimUtil.playTranslationX(
                listOf(rightMenu),
                speed * 100L,
                100f,
                0f
            ).forEach {
                it.interpolator(BounceInterpolator()).start()
            }
            AnimUtil.playTranslationY(
                listOf(start, version, jar),
                speed * 100L,
                -200f,
                0f
            ).forEachIndexed { index, objectAnimator ->
                objectAnimator.interpolator(BounceInterpolator()).startAfter((index + 1) * 100L)
            }
            AnimUtil.playTranslationY(
                listOf(home, manage, controller, setting, back),
                speed * 100L,
                -300f,
                0f
            ).forEachIndexed { index, objectAnimator ->
                objectAnimator.interpolator(BounceInterpolator()).startAfter((index + 1) * 100L)
            }
            AnimUtil.playTranslationX(
                listOf(home, manage, controller, setting, back),
                speed * 100L,
                -100f,
                0f
            ).forEachIndexed { index, objectAnimator ->
                objectAnimator.interpolator(BounceInterpolator()).startAfter((index + 1) * 100L)
            }
        }
    }

    private fun shareLog() {
        try {
            val file = File(FCLPath.LOG_DIR).resolve("latest_game.log")
            if (!file.exists()) return
            val intent = Intent(Intent.ACTION_SEND)

            val uri = FileProvider.getUriForFile(
                this,
                "${application.packageName}.provider",
                file
            )
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(
                Intent.createChooser(
                    intent,
                    getString(top.newblock.fcl.R.string.crash_reporter_share)
                )
            )
        } catch (e: Exception) {
            LOG.log(Level.INFO, "Share error: $e")
        }
    }

    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_DENIED
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            try {
                val intent = Intent()
                intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, applicationInfo.uid)
                startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
        } else {
            permissionResultLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun shouldPlayVideo(): Boolean {
        return File(FCLPath.LIVE_BACKGROUND_PATH).exists()
    }

    fun setupLiveBackground() {
        if (shouldPlayVideo()) {
            binding.videoView.visibility = View.VISIBLE
            binding.videoView.setVideoPath(FCLPath.LIVE_BACKGROUND_PATH)
            binding.videoView.setOnPreparedListener {
                mediaPlayer = it
                it.isLooping = true
                setLiveBackgroundVolume()
                binding.videoView.start()
            }
            binding.videoView.setOnCompletionListener {
                binding.videoView.seekTo(0)
                binding.videoView.start()
            }
            binding.videoView.setOnErrorListener { _, _, _ ->
                mediaPlayer = null
                return@setOnErrorListener true
            }
        } else {
            mediaPlayer = null
            binding.videoView.visibility = View.GONE
            binding.videoView.stopPlayback()
        }
    }

    fun setLiveBackgroundVolume() {
        mediaPlayer?.let {
            val volume = sharedPreferences.getInt("videoBackgroundVolume", 100) / 100f
            it.setVolume(volume, volume)
        }
    }

    private fun handleModpack(intent: Intent) {
        val path = intent.getStringExtra("modpack_cache_path") ?: return
        modpackHandled = true
        val file = File(path)
        if (!file.exists()) return
        Toast.makeText(
            this,
            getString(R.string.modpack_external_detected, file.name),
            Toast.LENGTH_SHORT
        ).show()
        val downloadUI = uiManager.downloadUI
        downloadUI.runAfterInit {
            val page = LocalModpackPage(
                this,
                PageManager.PAGE_ID_TEMP,
                downloadUI.container,
                R.layout.page_modpack,
                profile,
                null,
                file
            )
            downloadUI.pageManager.showTempPage(page)
        }
    }

    private fun refreshScreenSize() {
        DisplayUtil.screenWidth = binding.root.width
        DisplayUtil.screenHeight = binding.root.height
    }

    private fun launchServerModpack() {
        if (isLaunchingModpack) return
        isLaunchingModpack = true

        val progress = ProgressDialog(this)

        Task.supplyAsync {
            HttpRequest.HttpGetRequest.GET(MODPACK_URL).getJson(ServerModpack::class.java)
        }.thenApplyAsync<ModpackCheck, Exception> { info ->
            val minVersion = info?.minForceClientVersion?.toIntOrNull() ?: 0
            val needUpdate = minVersion > 0 && minVersion > currentVersionCode()
            val urlList = info?.ModpackUrl ?: emptyList()
            ModpackCheck(
                urls = if (urlList.isEmpty() || needUpdate) emptyList() else sortUrlsBySpeed(urlList),
                needUpdate = needUpdate,
                minVersion = minVersion,
                sha1 = info?.Sha1
            )
        }.thenAcceptAsync<Exception> { check ->
            Schedulers.androidUIThread().execute {
                progress.dismiss()
                if (check.needUpdate) {
                    showUpdateRequired(check.minVersion)
                } else if (check.urls.isEmpty()) {
                    showModpackError("获取服务器整合包信息失败")
                } else {
                    downloadModpack(check.urls, check.sha1)
                }
            }
        }.start()
    }

    private fun downloadModpack(urls: List<URL>, sha1: String?) {
        val file = try {
            File.createTempFile("modpack", ".mrpack", cacheDir)
        } catch (e: IOException) {
            showModpackError("创建临时文件失败")
            return
        }

        val taskDialog = TaskDialog(this, TaskCancellationAction { dialog: TaskDialog -> dialog.dismiss() })
        taskDialog.setTitle(getString(R.string.message_downloading))
        val executor = FileDownloadTask(urls, file, FileDownloadTask.IntegrityCheck.of("SHA-1", sha1))
            .whenComplete(Schedulers.androidUIThread()) { e ->
                if (e == null) {
                    installAndLaunch(file)
                } else if (e is CancellationException) {
                    Toast.makeText(this, getString(R.string.message_cancelled), Toast.LENGTH_SHORT).show()
                } else {
                    showModpackError("下载整合包失败：$e")
                }
            }.executor()
        taskDialog.setExecutor(executor)
        taskDialog.show()
        executor.start()
    }

    private fun installAndLaunch(file: File) {
        Task.supplyAsync {
            runCatching {
                val charset = CompressingUtils.findSuitableEncoding(file.toPath())
                val modpack = ModpackHelper.readModpackManifest(file.toPath(), charset)
                val name = modpack.name
                Pair(name, modpack)
            }.getOrNull()
        }.thenAcceptAsync<Exception> { result ->
            Schedulers.androidUIThread().execute {
                if (result == null) {
                    showModpackError("读取整合包信息失败")
                } else {
                    val (name, modpack) = result
                    val profile = Profiles.getSelectedProfile()
                    if (profile.repository.hasVersion(name)) {
                        isLaunchingModpack = false
                        profile.selectedVersion = name
                        Versions.launch(this@MainActivity, profile, name)
                    } else {
                        installModpackAndLaunch(profile, file, name, modpack)
                    }
                }
            }
        }.start()
    }

    private fun installModpackAndLaunch(profile: Profile, file: File, name: String, modpack: Modpack) {
        val installTask = ModpackHelper.getInstallTask(profile, file, name, modpack)
        val taskDialog = TaskDialog(this, TaskCancellationAction { dialog: TaskDialog -> dialog.dismiss() })
        taskDialog.setTitle(getString(R.string.install_modpack))
        val executor = installTask.executor(object : TaskListener() {
            override fun onStop(success: Boolean, executor: TaskExecutor) {
                Schedulers.androidUIThread().execute {
                    if (success) {
                        isLaunchingModpack = false
                        profile.selectedVersion = name
                        Versions.launch(this@MainActivity, profile, name)
                    } else {
                        showModpackError("安装整合包失败：${executor.exception}")
                    }
                }
            }
        })
        taskDialog.setExecutor(executor)
        taskDialog.show()
        executor.start()
    }

    private fun sortUrlsBySpeed(urlStrings: List<String>): List<URL> {
        val speedMap = ConcurrentHashMap<String, Long>()
        val futures = urlStrings.map { s ->
            CompletableFuture.runAsync {
                val start = System.currentTimeMillis()
                try {
                    val conn = URL(s).openConnection() as HttpURLConnection
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.connect()
                    conn.responseCode
                    conn.disconnect()
                    speedMap[s] = System.currentTimeMillis() - start
                } catch (e: Exception) {
                    speedMap[s] = Long.MAX_VALUE
                }
            }
        }
        try {
            CompletableFuture.allOf(*futures.toTypedArray()).get(6, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // 测速超时，忽略，继续用已测得的结果
        }
        return urlStrings
            .sortedBy { speedMap.getOrDefault(it, Long.MAX_VALUE) }
            .map { NetworkUtils.toURL(it) }
    }

    private fun showModpackError(message: String) {
        isLaunchingModpack = false
        FCLAlertDialog.Builder(this)
            .setAlertLevel(FCLAlertDialog.AlertLevel.ALERT)
            .setMessage(message)
            .setNegativeButton(null)
            .create()
            .show()
    }

    private fun currentVersionCode(): Int {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: Exception) {
            0
        }
    }

    private fun showUpdateRequired(minVersion: Int) {
        isLaunchingModpack = false
        FCLAlertDialog.Builder(this)
            .setAlertLevel(FCLAlertDialog.AlertLevel.ALERT)
            .setMessage("启动器版本过低，需要更新到版本 $minVersion 及以上才能启动游戏")
            .setNegativeButton(null)
            .create()
            .show()
    }

    private data class ModpackCheck(
        val urls: List<URL>,
        val needUpdate: Boolean,
        val minVersion: Int,
        val sha1: String?
    )

    class ServerModpack {
        var MinecraftVer: String? = null
        var Sha1: String? = null
        var minForceClientVersion: String? = null
        var ModpackUrl: List<String>? = null
    }
}