package org.jjazzlab.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var startXButton: Button
    private lateinit var toggleLogButton: Button
    private lateinit var webView: WebView
    private lateinit var mainContainer: FrameLayout

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var xProcess: Process? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
        }

        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        startXButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 4, 4, 4) }
            text = "Start JJazzLab (Optimized)"
            setOnClickListener {
                startXButton.isEnabled = false
                appendOutput("Starting JJazzLab with font & Java2D optimizations...\n")
                executor.execute { startStepD() }
            }
        }

        toggleLogButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 4, 4, 4) }
            text = "Toggle Log"
            setOnClickListener {
                if (webView.visibility == View.VISIBLE) {
                    webView.visibility = View.GONE
                } else {
                    webView.visibility = View.VISIBLE
                }
            }
        }

        buttonContainer.addView(startXButton)
        buttonContainer.addView(toggleLogButton)

        mainContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { weight = 1f }
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        statusTextView = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            text = "Optimized JJazzLab Desktop Launch.\nTap 'Start JJazzLab (Optimized)'.\n\n"
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            clearCache(true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    appendOutput("\n[WebView Error] ${error?.description} (code ${error?.errorCode})\n")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        appendOutput("\n[JS Console] ${it.message()}: line ${it.lineNumber()} of ${it.sourceId()}\n")
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }
        }

        scrollView.addView(statusTextView)
        mainContainer.addView(scrollView)
        mainContainer.addView(webView)

        rootLayout.addView(buttonContainer)
        rootLayout.addView(mainContainer)
        setContentView(rootLayout)
    }

    private fun appendOutput(text: String) {
        handler.post {
            statusTextView.append(text)
        }
    }

    private fun startStepD() {
        try {
            val rootfsDir = File(filesDir, "rootfs")
            val rootDir = File(rootfsDir, "root").apply { mkdirs() }
            val novncDir = File(rootfsDir, "opt/novnc")
            val vncHtml = File(novncDir, "vnc.html")

            if (!vncHtml.exists()) {
                appendOutput("Setting up rootfs packages & noVNC...\n")
                val prepCmd = "DEBIAN_FRONTEND=noninteractive apt-get remove -y --purge novnc || true; " +
                              "rm -rf /opt/novnc /usr/share/novnc; " +
                              "DEBIAN_FRONTEND=noninteractive apt-get update && " +
                              "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends tigervnc-standalone-server python3-websockify python3-numpy openbox xterm x11-utils x11-xserver-utils fonts-dejavu fonts-liberation dbus-x11 wget ca-certificates openjdk-21-jdk fontconfig"
                runProotCommand(prepCmd)

                val tarFile = File(cacheDir, "noVNC-1.4.0.tar.gz")
                if (!tarFile.exists()) {
                    val url = URL("https://github.com/novnc/noVNC/archive/refs/tags/v1.4.0.tar.gz")
                    url.openStream().use { input ->
                        FileOutputStream(tarFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                val rootfsOpt = File(rootfsDir, "opt").apply { mkdirs() }
                extractTarGzToOpt(tarFile, rootfsOpt)
            }

            val optJjazzlab = File(rootfsDir, "opt/jjazzlab")
            if (!optJjazzlab.exists()) {
                appendOutput("Extracting JJazzLab cluster from assets to /opt/jjazzlab...\n")
                optJjazzlab.mkdirs()
                copyAssetFolder("jjazzlab", optJjazzlab)
                appendOutput("JJazzLab cluster extraction complete.\n")
            }

            val scriptFile = File(rootDir, "start-x.sh")
            scriptFile.writeText(
                """
                #!/bin/bash
                export XDG_RUNTIME_DIR=/tmp/runtime-root
                mkdir -p /tmp/runtime-root /tmp/.X11-unix /root/.vnc /opt
                chmod 1777 /tmp/.X11-unix /tmp/runtime-root
                rm -f /tmp/.X11-unix/X1 /tmp/.X1-lock

                pkill -f websockify || true
                pkill -f Xvnc || true

                echo "Refreshing fontconfig cache..." > /root/x.log
                fc-cache -f >/dev/null 2>&1 || true

                echo "Starting Xvnc at 1280x800 depth 24..." >> /root/x.log

                (
                    while true; do
                        Xvnc :1 -geometry 1280x800 -depth 24 -SecurityTypes None -AlwaysShared -rfbport 5901 >> /root/xvnc.log 2>&1
                        echo "Xvnc exited with ${'$'}?" >> /root/x.log
                        sleep 2
                    done
                ) &

                export DISPLAY=:1

                (
                    while true; do
                        websockify --web /opt/novnc 6080 127.0.0.1:5901 >> /root/websockify.log 2>&1
                        echo "websockify exited with ${'$'}?" >> /root/x.log
                        sleep 2
                    done
                ) &

                echo "Starting openbox..." >> /root/x.log
                openbox &
                sleep 2

                echo "Starting JJazzLab desktop app (optimized)..." >> /root/x.log
                chmod +x /opt/jjazzlab/bin/jjazzlab
                export PATH=${'$'}PATH:/usr/lib/jvm/java-21-openjdk-amd64/bin
                /opt/jjazzlab/bin/jjazzlab --jdkhome /usr/lib/jvm/java-21-openjdk-amd64 -J-Dsun.java2d.opengl=false -J-Dsun.java2d.xrender=false >> /root/jjazzlab.log 2>&1 &

                exec tail -f /dev/null
                """.trimIndent() + "\n"
            )

            appendOutput("Wrote optimized /root/start-x.sh. Launching background proot daemon...\n")

            if (xProcess == null) {
                val startCmd = "chmod +x /root/start-x.sh && /root/start-x.sh >/root/x.log 2>&1 </dev/null"
                startProotBackgroundProcess(startCmd)
            }

            appendOutput("Waiting until http://127.0.0.1:6080/vnc.html returns 200 AND socket 5901 returns RFB...\n")
            var ready = false
            for (i in 1..60) {
                Thread.sleep(1000)
                val httpOk = checkHttp200("http://127.0.0.1:6080/vnc.html")
                val rfbOk = checkRfb("127.0.0.1", 5901)
                if (httpOk && rfbOk) {
                    appendOutput("READY on attempt $i! HTTP 200 + RFB banner verified.\n")
                    ready = true
                    break
                }
            }

            if (ready) {
                handler.post {
                    webView.visibility = View.VISIBLE
                    val targetUrl = "http://127.0.0.1:6080/vnc.html?autoconnect=true&resize=scale&quality=9&compression=0&path=websockify&v=${System.currentTimeMillis()}"
                    webView.loadUrl(targetUrl)
                    appendOutput("\nLoaded optimized JJazzLab noVNC URL in WebView:\n$targetUrl\n")
                }
            } else {
                appendOutput("Timeout waiting for HTTP 200 + RFB banner.\n")
            }

        } catch (e: Exception) {
            appendOutput("Start Optimized Step D Error: ${e.message}\n")
        } finally {
            handler.post { startXButton.isEnabled = true }
        }
    }

    private fun copyAssetFolder(assetFolder: String, outputDir: File) {
        val assetManager = assets
        val files = assetManager.list(assetFolder) ?: return
        for (filename in files) {
            val assetPath = "$assetFolder/$filename"
            val outFile = File(outputDir, filename)
            val subFiles = assetManager.list(assetPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                outFile.mkdirs()
                copyAssetFolder(assetPath, outFile)
            } else {
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (filename.startsWith("jjazzlab") || filename.endsWith(".sh") || outFile.absolutePath.contains("/bin/")) {
                    outFile.setExecutable(true, false)
                }
            }
        }
    }

    private fun checkHttp200(urlString: String): Boolean {
        return try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun checkRfb(host: String, port: Int): Boolean {
        return try {
            Socket(host, port).use { socket ->
                socket.soTimeout = 2000
                val buf = ByteArray(12)
                val read = socket.getInputStream().read(buf)
                if (read >= 4) {
                    val banner = String(buf, 0, read)
                    banner.startsWith("RFB ")
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun extractTarGzToOpt(tarGzFile: File, optDir: File) {
        FileInputStream(tarGzFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                GzipCompressorInputStream(bis).use { gzis ->
                    TarArchiveInputStream(gzis).use { tais ->
                        var entry = tais.nextTarEntry
                        while (entry != null) {
                            val name = entry.name.replaceFirst(Regex("^[^/]+/"), "")
                            if (name.isNotEmpty()) {
                                val newFile = File(optDir, "novnc/$name")
                                if (entry.isDirectory) {
                                    newFile.mkdirs()
                                } else {
                                    newFile.parentFile?.mkdirs()
                                    FileOutputStream(newFile).use { fos ->
                                        tais.copyTo(fos)
                                    }
                                    newFile.setExecutable((entry.mode and 64) != 0, false)
                                }
                            }
                            entry = tais.nextTarEntry
                        }
                    }
                }
            }
        }
    }

    private fun startProotBackgroundProcess(scriptText: String) {
        val nativeLibDir = applicationInfo.nativeLibraryDir
        val prootBinary = File(nativeLibDir, "libproot.so")
        val prootLoader = File(nativeLibDir, "libproot-loader.so")
        val prootTmp = File(cacheDir, "proot-tmp").apply { mkdirs() }
        val rootfsDir = File(filesDir, "rootfs")

        val command = listOf(
            prootBinary.absolutePath,
            "--link2symlink",
            "--sysvipc",
            "-0",
            "-r", rootfsDir.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/root",
            "/usr/bin/env",
            "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm",
            "/bin/bash",
            "-c",
            scriptText
        )

        val pb = ProcessBuilder(command)
        pb.environment()["LD_LIBRARY_PATH"] = "${filesDir.absolutePath}:$nativeLibDir"
        pb.environment()["PROOT_LOADER"] = prootLoader.absolutePath
        pb.environment()["PROOT_TMP_DIR"] = prootTmp.absolutePath
        pb.redirectErrorStream(true)

        xProcess = pb.start()
    }

    private fun runProotCommand(scriptText: String): Int {
        val nativeLibDir = applicationInfo.nativeLibraryDir
        val prootBinary = File(nativeLibDir, "libproot.so")
        val prootLoader = File(nativeLibDir, "libproot-loader.so")
        val prootTmp = File(cacheDir, "proot-tmp").apply { mkdirs() }
        val rootfsDir = File(filesDir, "rootfs")

        val command = listOf(
            prootBinary.absolutePath,
            "--link2symlink",
            "--sysvipc",
            "-0",
            "-r", rootfsDir.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/root",
            "/usr/bin/env",
            "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm",
            "/bin/bash",
            "-c",
            scriptText
        )

        val pb = ProcessBuilder(command)
        pb.environment()["LD_LIBRARY_PATH"] = "${filesDir.absolutePath}:$nativeLibDir"
        pb.environment()["PROOT_LOADER"] = prootLoader.absolutePath
        pb.environment()["PROOT_TMP_DIR"] = prootTmp.absolutePath
        pb.redirectErrorStream(true)

        val process = pb.start()
        process.inputStream.bufferedReader().use { reader ->
            var line = reader.readLine()
            while (line != null) {
                appendOutput("$line\n")
                line = reader.readLine()
            }
        }

        return process.waitFor()
    }
}
