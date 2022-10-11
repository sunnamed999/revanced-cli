package app.revanced.utils.adb

import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.RemoteFile
import java.io.File

private fun JadbDevice.startCommand(command: String, su: Boolean = true) =
    shellProcessBuilder(if (su) "su -c '$command'" else command).start()

internal fun JadbDevice.run(command: String, su: Boolean = true) =
    this.startCommand(command, su).inputStream.bufferedReader()

internal fun JadbDevice.hasSu() =
    this.startCommand("su -h", false).waitFor() == 0

internal fun JadbDevice.copyFile(file: File, targetFile: String) =
    push(file, RemoteFile(targetFile))

internal fun JadbDevice.createFile(targetFile: String, content: String) =
    push(content.byteInputStream(), System.currentTimeMillis(), 644, RemoteFile(targetFile))
