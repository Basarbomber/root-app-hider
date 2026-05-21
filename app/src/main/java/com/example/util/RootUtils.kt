package com.example.util

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.File

object RootUtils {
    // Basic root check
    fun isRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        
        // Also try running "which su"
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            reader.close()
            process.destroy()
            line != null && line.isNotEmpty()
        } catch (t: Throwable) {
            false
        }
    }

    data class ShellResult(
        val success: Boolean,
        val command: String,
        val output: String,
        val error: String
    )

    fun executeAsRoot(command: String): ShellResult {
        var process: Process? = null
        var os: DataOutputStream? = null
        var stdoutReader: BufferedReader? = null
        var stderrReader: BufferedReader? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                stdout.append(line).append("\n")
            }
            while (stderrReader.readLine().also { line = it } != null) {
                stderr.append(line).append("\n")
            }

            val exitVal = process.waitFor()
            val isSuccess = exitVal == 0

            return ShellResult(
                success = isSuccess,
                command = "su -c \"$command\"",
                output = stdout.toString().trim(),
                error = stderr.toString().trim()
            )
        } catch (e: Exception) {
            return ShellResult(
                success = false,
                command = "su -c \"$command\"",
                output = "",
                error = e.localizedMessage ?: "Failed to execute root command"
            )
        } finally {
            try { os?.close() } catch (ignored: Exception) {}
            try { stdoutReader?.close() } catch (ignored: Exception) {}
            try { stderrReader?.close() } catch (ignored: Exception) {}
            try { process?.destroy() } catch (ignored: Exception) {}
        }
    }
}
