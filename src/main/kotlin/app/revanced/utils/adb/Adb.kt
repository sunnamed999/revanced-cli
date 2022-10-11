package app.revanced.utils.adb

import app.revanced.cli.logging.CliLogger
import se.vidstige.jadb.JadbConnection
import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.managers.Package
import se.vidstige.jadb.managers.PackageManager
import java.io.Closeable
import java.io.File
import java.nio.file.Files

internal sealed class Adb(deviceSerial: String) : Closeable {
    protected val device: JadbDevice = JadbConnection().devices.find { it.serial == deviceSerial }
        ?: throw IllegalArgumentException("The device with the serial $deviceSerial can not be found.")

    protected val packageManager = PackageManager(device)

    open val logger: CliLogger? = null

    abstract fun install(base: Apk, splits: List<Apk>)

    abstract fun uninstall(packageName: String)
    override fun close() {
        logger?.trace("Closed")
    }

    class RootAdb(deviceSerial: String, override val logger: CliLogger? = null) : Adb(deviceSerial) {
        init {
            if (!device.hasSu()) throw IllegalArgumentException("Root required on $deviceSerial. Task failed")
        }

        override fun install(base: Apk, splits: List<Apk>) {
            TODO("Install with root")
        }

        override fun uninstall(packageName: String) {
            TODO("Uninstall with root")
        }
    }

    class UserAdb(deviceSerial: String, override val logger: CliLogger? = null) : Adb(deviceSerial) {
        private val replaceRegex = Regex("\\D+") // all non-digits
        override fun install(base: Apk, splits: List<Apk>) {
            logger?.info("Installing ${base.apk}")

            /**
             * Class storing the information required for the installation of an apk.
             *
             * @param apk The apk.
             * @param size The size of the apk file. Inferred by default from [apk].
             */
            data class ApkInfo(val apk: Apk, val size: Long = Files.size(apk.file.toPath()))

            val sizes = buildList {
                val add = { apk: Apk -> add(ApkInfo(apk, Files.size(apk.file.toPath()))) }

                add(base)
                for (split in splits) add(split)
            }

            device.run("pm install-create -S ${sizes.sumOf { it.size }}").readLine().also { output ->
                output.replace(replaceRegex, "")
            }.also { sid ->
                logger?.trace("Created session $sid")

                sizes.onEachIndexed { index, (apk, size) ->
                    val targetFilePath = "/sdcard/${apk.file.name}"
                    device.copyFile(apk.file, targetFilePath)

                    device.run("pm install-write -S $size $sid $index $targetFilePath")
                    device.run("rm $targetFilePath")
                }
            }.let { sid ->
                device.run("pm install-commit $sid")
                logger?.trace("Committed session $sid")
            }
        }

        override fun uninstall(packageName: String) {
            logger?.info("Uninstalling $packageName")

            packageManager.uninstall(Package(packageName))
        }
    }

    /**
     * Apk file for [Adb].
     *
     * @param apk The [app.revanced.patcher.apk.Apk] file.
     * @param file The [File] of the [app.revanced.patcher.apk.Apk] file. Inferred by default from [apk].
     */
    class Apk(val apk: app.revanced.patcher.apk.Apk, val file: File = apk.file)
}