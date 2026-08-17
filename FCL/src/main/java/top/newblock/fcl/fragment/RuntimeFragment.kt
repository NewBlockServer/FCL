package top.newblock.fcl.fragment

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import top.newblock.fcl.R
import top.newblock.fcl.activity.SplashActivity
import top.newblock.fcl.databinding.FragmentRuntimeBinding
import top.newblock.fcl.util.RuntimeUtils
import com.tungsten.fclauncher.utils.Architecture
import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fclcore.util.Logging
import com.tungsten.fcllibrary.component.FCLFragment
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuntimeFragment : FCLFragment(), View.OnClickListener {
    private lateinit var bind: FragmentRuntimeBinding
    var lwjgl = false
    var cacio = false
    var cacio17 = false
    var java25 = false
    var jna = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_runtime, container, false)
        bind = FragmentRuntimeBinding.bind(view)
        bind.install.setOnClickListener(this)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { initState() }
            refreshDrawables()
            check()
        }
        return view
    }

    private fun initState() {
        lwjgl = (activity as SplashActivity).lwjgl
        cacio = (activity as SplashActivity).cacio
        cacio17 = (activity as SplashActivity).cacio17
        java25 = (activity as SplashActivity).java25
        jna = (activity as SplashActivity).jna
    }

    private fun refreshDrawables() {
        if (context != null) {
            val stateUpdate =
                AppCompatResources.getDrawable(requireContext(), R.drawable.ic_baseline_update_24)
            val stateDone =
                AppCompatResources.getDrawable(requireContext(), R.drawable.ic_baseline_done_24)

            stateUpdate?.setTint(Color.GRAY)
            stateDone?.setTint(Color.GRAY)

            bind.apply {
                lwjglState.setBackgroundDrawable(if (lwjgl) stateDone else stateUpdate)
                cacioState.setBackgroundDrawable(if (cacio) stateDone else stateUpdate)
                cacio17State.setBackgroundDrawable(if (cacio17) stateDone else stateUpdate)
                java25State.setBackgroundDrawable(if (java25) stateDone else stateUpdate)
                jnaState.setBackgroundDrawable(if (jna) stateDone else stateUpdate)
            }
        }
    }

    private val isLatest: Boolean
        get() = lwjgl && cacio && cacio17 && java25 && jna

    private fun check() {
        if (isLatest) {
            (activity as SplashActivity).enterLauncher()
        }
    }

    private var installing = false

    private fun install() {
        if (installing) return

        bind.apply {
            installing = true
            if (!lwjgl) {
                lwjglState.visibility = View.GONE
                lwjglProgress.visibility = View.VISIBLE
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            RuntimeUtils.install(context, FCLPath.LWJGL_DIR, "app_runtime/lwjgl")
                            lwjgl = true
                        }
                    }
                    lwjglState.visibility = View.VISIBLE
                    lwjglProgress.visibility = View.GONE
                    refreshDrawables()
                    check()
                }
            }
            if (!cacio) {
                cacioState.visibility = View.GONE
                cacioProgress.visibility = View.VISIBLE
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            RuntimeUtils.install(
                                context,
                                FCLPath.CACIOCAVALLO_8_DIR,
                                "app_runtime/caciocavallo"
                            )
                            cacio = true
                        }
                    }
                    cacioState.visibility = View.VISIBLE
                    cacioProgress.visibility = View.GONE
                    refreshDrawables()
                    check()
                }
            }
            if (!cacio17) {
                cacio17State.visibility = View.GONE
                cacio17Progress.visibility = View.VISIBLE
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            RuntimeUtils.install(
                                context,
                                FCLPath.CACIOCAVALLO_17_DIR,
                                "app_runtime/caciocavallo17"
                            )
                            cacio17 = true
                        }
                    }
                    cacio17State.visibility = View.VISIBLE
                    cacio17Progress.visibility = View.GONE
                    refreshDrawables()
                    check()
                }
            }
            if (!java25) {
                java25State.visibility = View.GONE
                java25Progress.visibility = View.VISIBLE
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            RuntimeUtils.installJava(
                                context,
                                FCLPath.JAVA_25_PATH,
                                "app_runtime/java/jre25"
                            )
                            java25 = true
                        }.exceptionOrNull()?.let { showErrorDialog(it.toString()) }
                    }
                    java25State.visibility = View.VISIBLE
                    java25Progress.visibility = View.GONE
                    refreshDrawables()
                    check()
                }
            }
            if (!jna) {
                jnaState.visibility = View.GONE
                jnaProgress.visibility = View.VISIBLE
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            RuntimeUtils.installJna(
                                context,
                                FCLPath.JNA_PATH,
                                "app_runtime/jna"
                            )
                            jna = true
                        }
                    }
                    jnaState.visibility = View.VISIBLE
                    jnaProgress.visibility = View.GONE
                    refreshDrawables()
                    check()
                }
            }
        }
    }

    override fun onClick(view: View) {
        if (view === bind.install) {
            val deviceArch = Architecture.archAsString(Architecture.getDeviceArchitecture())
            if (!isJavaArchSupported(deviceArch)) {
                showErrorDialog(
                    getString(
                        R.string.missing_runtime_arch_files,
                        deviceArch,
                        "FCL-release-x.x.x.x-$deviceArch.apk",
                        "FCL-release-x.x.x.x-all.apk"
                    )
                )
                return
            }
            install()
        }
    }

    private fun isJavaArchSupported(arch: String): Boolean {
        try {
            val javaDirs = listOf("jre25")
            val assetManager = requireContext().assets
            var supportedCount = 0
            for (javaDir in javaDirs) {
                val dirPath = "app_runtime/java/$javaDir"
                val files = assetManager.list(dirPath)
                if (files != null) {
                    val expectedFile = "bin-$arch.tar.xz"
                    if (files.contains(expectedFile)) {
                        supportedCount++
                    }
                }
            }
            return supportedCount > 0
        } catch (e: Exception) {
            showErrorDialog(e.toString())
            return false
        }
    }

    private fun showErrorDialog(message: String) {
        installing = false
        lifecycleScope.launch(Dispatchers.Main) {
            FCLAlertDialog.Builder(requireContext())
                .setMessage(message)
                .setPositiveButton {
                }
                .create()
                .show()
        }
    }
}
