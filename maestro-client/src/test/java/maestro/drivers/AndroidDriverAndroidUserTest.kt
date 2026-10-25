package maestro.drivers

import com.google.common.truth.Truth.assertThat
import dadb.AdbShellResponse
import dadb.AdbStream
import dadb.Dadb
import org.junit.jupiter.api.Test
import java.io.File

class AndroidDriverAndroidUserTest {

    /** Records shell commands; packages are "installed" per user as given. */
    private class FakeDadb(private val installed: Map<Int, Set<String>>) : Dadb {
        val commands = mutableListOf<String>()
        val installs = mutableListOf<File>()

        override fun shell(command: String): AdbShellResponse {
            commands += command
            val listPackages = Regex("""^pm list packages --user (\d+) (\S+)$""").find(command)
            if (listPackages != null) {
                val (user, packageName) = listPackages.destructured
                val present = installed[user.toInt()].orEmpty().contains(packageName)
                return AdbShellResponse(if (present) "package:$packageName\n" else "", "", 0)
            }
            return AdbShellResponse("", "", 0)
        }

        override fun install(file: File, vararg options: String) {
            installs += file
        }

        override fun open(destination: String): AdbStream = throw UnsupportedOperationException(destination)
        override fun supportsFeature(feature: String): Boolean = false
        override fun close() = Unit
    }

    @Test
    fun `maestro driver app is looked up in the primary user when targeting another user`() {
        val dadb = FakeDadb(installed = mapOf(0 to setOf("dev.mobile.maestro")))
        val driver = AndroidDriver(dadb, reinstallDriver = false, androidUser = 10)

        driver.installMaestroDriverApp()

        assertThat(dadb.commands).contains("pm list packages --user 0 dev.mobile.maestro")
        assertThat(dadb.installs).isEmpty()
    }

    @Test
    fun `clear state checks and clears the app in the target user`() {
        val dadb = FakeDadb(installed = mapOf(10 to setOf("a.b")))
        val driver = AndroidDriver(dadb, androidUser = 10)

        driver.clearAppState("a.b")

        assertThat(dadb.commands).containsExactly("pm list packages --user 10 a.b", "pm clear --user 10 a.b").inOrder()
    }

    @Test
    fun `clear state without a target user uses upstream's commands`() {
        val dadb = FakeDadb(installed = mapOf(0 to setOf("a.b")))
        val driver = AndroidDriver(dadb)

        driver.clearAppState("a.b")

        assertThat(dadb.commands).containsExactly("pm list packages --user 0 a.b", "pm clear a.b").inOrder()
    }

    @Test
    fun `stop app targets the user only when one is set`() {
        val withUser = FakeDadb(installed = emptyMap())
        AndroidDriver(withUser, androidUser = 10).stopApp("a.b")
        val withoutUser = FakeDadb(installed = emptyMap())
        AndroidDriver(withoutUser).stopApp("a.b")

        assertThat(withUser.commands).containsExactly("am force-stop --user 10 a.b")
        assertThat(withoutUser.commands).containsExactly("am force-stop a.b")
    }
}
