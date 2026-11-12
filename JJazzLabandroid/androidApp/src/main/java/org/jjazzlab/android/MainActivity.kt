package org.jjazzlab.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var startXButton: Button
    private lateinit var diagButton: Button
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
            text = "Start X Display"
            setOnClickListener {
                startXButton.isEnabled = false
                appendOutput("Starting Xvnc & noVNC server...\n")
                executor.execute { startXDisplay() }
            }
        }

        diagButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 4, 4, 4) }
            text = "Run X11 Diagnostics"
            setOnClickListener {
                diagButton.isEnabled = false
                appendOutput("Running X11 diagnostics...\n")
                executor.execute { runX11Diagnostics() }
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
        buttonContainer.addView(diagButton)
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
            text = "Step (c): Virtual X Display in Proot.\nTap 'Start X Display' then 'Run X11 Diagnostics'.\n\n"
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            webViewClient = WebViewClient()
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

    private fun runX11Diagnostics() {
        try {
            val rootfsDir = File(filesDir, "rootfs")
            val xLog = File(rootfsDir, "root/x.log")

            appendOutput("========================================\n")
            appendOutput("Check 1: Last 50 lines of /root/x.log:\n")
            if (xLog.exists()) {
                appendOutput(xLog.readLines().takeLast(50).joinToString("\n") + "\n")
            } else {
                appendOutput("/root/x.log does not exist.\n")
            }

            appendOutput("\nCheck 1b: VNC log files in /root/.vnc/:\n")
            val vncDir = File(rootfsDir, "root/.vnc")
            if (vncDir.exists()) {
                vncDir.listFiles()?.forEach { f ->
                    if (f.name.endsWith(".log")) {
                        appendOutput("--- ${f.name} ---\n")
                        appendOutput(f.readLines().takeLast(30).joinToString("\n") + "\n")
                    }
                }
            }

            appendOutput("\nCheck 2: Running processes (ps aux):\n")
            runProotCommand("ps aux | grep -E 'Xvnc|websockify|openbox|xterm'")

            appendOutput("\nCheck 3: Listening ports:\n")
            runProotCommand("ss -ltnp || netstat -ltnp || lsof -i :5901")

            appendOutput("\nCheck 4: xdpyinfo -display :1:\n")
            runProotCommand("xdpyinfo -display :1 | head -n 5")

            appendOutput("\nCheck 6: Plain TCP check to port 5901:\n")
            runProotCommand("bash -c 'timeout 2 bash -c \"cat < /dev/tcp/127.0.0.1/5901\"'")

        } catch (e: Exception) {
            appendOutput("Diag Error: ${e.message}\n")
        } finally {
            handler.post { diagButton.isEnabled = true }
        }
    }

    private fun startXDisplay() {
        try {
            val rootfsDir = File(filesDir, "rootfs")
            val rootDir = File(rootfsDir, "root").apply { mkdirs() }
            val vncHtml = File(rootfsDir, "usr/share/novnc/vnc.html")

            if (!vncHtml.exists()) {
                appendOutput("Installing X11 packages first via apt-get...\n")
                val aptCmd = "DEBIAN_FRONTEND=noninteractive apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends tigervnc-standalone-server novnc websockify openbox xterm x11-utils x11-xserver-utils fonts-dejavu fonts-liberation dbus-x11 procps net-tools"
                runProotCommand(aptCmd)
            }

            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            val w = if (metrics.widthPixels > 0) metrics.widthPixels else 1280
            val h = if (metrics.heightPixels > 0) metrics.heightPixels else 800

            val scriptFile = File(rootDir, "start-x.sh")
            scriptFile.writeText(
                """
                #!/bin/bash
                export XDG_RUNTIME_DIR=/tmp/runtime-root
                mkdir -p /tmp/runtime-root /tmp/.X11-unix /root/.vnc
                chmod 1777 /tmp/.X11-unix /tmp/runtime-root
                rm -f /tmp/.X11-unix/X1 /tmp/.X1-lock

                exec > /root/x.log 2>&1

                echo "Starting Xvnc on :1 (${w}x${h})..."
                Xvnc :1 -geometry ${w}x${h} -depth 24 -SecurityTypes None -AlwaysShared -noreset &

                export DISPLAY=:1

                echo "Starting websockify on 6080..."
                websockify --web /usr/share/novnc 6080 127.0.0.1:5901 &

                echo "Waiting for xdpyinfo..."
                for i in $(seq 1 30); do
                    if xdpyinfo -display :1 >/dev/null 2>&1; then
                        echo "Xvnc ready on :1"
                        break
                    fi
                    sleep 1
                done

                echo "Starting openbox..."
                openbox &

                echo "Starting xterm..."
                xterm -geometry 100x30 &

                echo "X11 stack fully initialized."
                """.trimIndent() + "\n"
            )

            appendOutput("Wrote /root/start-x.sh (${w}x${h}). Launching background proot process...\n")

            if (xProcess == null) {
                val startCmd = "chmod +x /root/start-x.sh && /root/start-x.sh"
                startProotBackgroundProcess(startCmd)
            }

            appendOutput("Polling http://127.0.0.1:6080/vnc.html (up to 60s)...\n")
            var ready = false
            for (i in 1..60) {
                Thread.sleep(1000)
                try {
                    val conn = URL("http://127.0.0.1:6080/vnc.html").openConnection() as HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    val code = conn.responseCode
                    if (code == 200) {
                        appendOutput("noVNC web server READY (HTTP 200) on attempt $i!\n")
                        ready = true
                        break
                    }
                } catch (_: Exception) {}
            }

            if (ready) {
                handler.post {
                    webView.visibility = View.VISIBLE
                    webView.loadUrl("http://127.0.0.1:6080/vnc_lite.html?host=127.0.0.1&port=6080&autoconnect=true&encrypt=false")
                    appendOutput("\nSwitched screen to full-screen WebView X display (vnc_lite.html)!\n")
                }
            } else {
                appendOutput("noVNC server poll timed out. Checking /root/x.log...\n")
                checkXLog()
            }

        } catch (e: Exception) {
            appendOutput("Start X Error: ${e.message}\n")
            checkXLog()
        } finally {
            handler.post { startXButton.isEnabled = true }
        }
    }

    private fun checkXLog() {
        try {
            val logFile = File(filesDir, "rootfs/root/x.log")
            if (logFile.exists()) {
                val lines = logFile.readLines().takeLast(30)
                appendOutput("\n--- Last 30 lines of /root/x.log ---\n")
                lines.forEach { appendOutput("$it\n") }
            } else {
                appendOutput("\n/root/x.log does not exist yet.\n")
            }
        } catch (e: Exception) {
            appendOutput("Failed to read /root/x.log: ${e.message}\n")
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
            "--kill-on-exit",
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
        Executors.newSingleThreadExecutor().execute {
            try {
                xProcess?.inputStream?.bufferedReader()?.use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        appendOutput("[x11] $line\n")
                        line = reader.readLine()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun runProotCommand(scriptText: String): Int {
        val nativeLibDir = applicationInfo.nativeLibraryDir
        val prootBinary = File(nativeLibDir, "libproot.so")
        val prootLoader = File(nativeLibDir, "libproot-loader.so")
        val prootTmp = File(cacheDir, "proot-tmp").apply { mkdirs() }
        val rootfsDir = File(filesDir, "rootfs")

        val command = listOf(
            prootBinary.absolutePath,
            "--kill-on-exit",
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
