package maestro.android

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AndroidUserCommandsTest {

    @Test
    fun `user flag is empty without a user`() {
        assertThat(AndroidUserCommands.userFlag(null)).isEqualTo("")
    }

    @Test
    fun `user flag names the user with a trailing space`() {
        assertThat(AndroidUserCommands.userFlag(10)).isEqualTo("--user 10 ")
    }

    @Test
    fun `launcher activities query targets the user`() {
        assertThat(AndroidUserCommands.queryLauncherActivitiesCommand(17, "a.b"))
            .isEqualTo("cmd package query-activities --brief --user 17 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER a.b")
    }

    @Test
    fun `launcher component is read from query-activities output`() {
        val output = "1 activities found:\n" +
            "  Activity #0:\n" +
            "    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\n" +
            "    net.codebakery.wildberry/.MainActivity\n"

        assertThat(AndroidUserCommands.parseLauncherComponent(output, "net.codebakery.wildberry"))
            .isEqualTo("net.codebakery.wildberry/.MainActivity")
    }

    @Test
    fun `first launcher component wins when an app has several, like getLaunchIntentForPackage`() {
        val output = "2 activities found:\n" +
            "  Activity #0:\n" +
            "    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\n" +
            "    a.b/.MainActivity\n" +
            "  Activity #1:\n" +
            "    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\n" +
            "    a.b/leakcanary.internal.activity.LeakLauncherActivity\n"

        assertThat(AndroidUserCommands.parseLauncherComponent(output, "a.b")).isEqualTo("a.b/.MainActivity")
    }

    @Test
    fun `no launcher component when nothing resolves`() {
        assertThat(AndroidUserCommands.parseLauncherComponent("", "a.b")).isNull()
        assertThat(AndroidUserCommands.parseLauncherComponent("No activities found\n", "a.b")).isNull()
    }

    @Test
    fun `launch failure message leaves out launch argument values`() {
        val command = AndroidUserCommands.amStartCommand(17, "a.b/.Main", mapOf("token" to "s3cret: x"))

        val message = AndroidUserCommands.launchFailureMessage("a.b", 17, command, "$command: Error: Activity class does not exist")

        assertThat(message).isEqualTo("Maestro failed to launch app a.b in user 17: Error: Activity class does not exist")
        assertThat(message).doesNotContain("s3cret")
    }

    @Test
    fun `launch failure message of an unknown shape keeps no command text`() {
        val command = AndroidUserCommands.amStartCommand(17, "a.b/.Main", mapOf("token" to "s3cret"))

        assertThat(AndroidUserCommands.launchFailureMessage("a.b", 17, command, "wrapped: $command"))
            .isEqualTo("Maestro failed to launch app a.b in user 17")
        assertThat(AndroidUserCommands.launchFailureMessage("a.b", 17, command, null))
            .isEqualTo("Maestro failed to launch app a.b in user 17")
    }

    @Test
    fun `launcher component of another package is ignored`() {
        assertThat(AndroidUserCommands.parseLauncherComponent("com.other/.Main\n", "a.b")).isNull()
    }

    @Test
    fun `am start passes every launch argument type as a quoted extra`() {
        val arguments = linkedMapOf<String, Any>(
            "s" to "it's \"\$HOME\" x",
            "b" to true,
            "i" to 42,
            "l" to 1234567890123L,
            "d" to 3.25,
        )

        assertThat(AndroidUserCommands.amStartCommand(17, "a.b/.Main", arguments)).isEqualTo(
            "am start --user 17 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10000000 -n a.b/.Main " +
                "--es 's' 'it'\\''s \"\$HOME\" x' --ez 'b' 'true' --ei 'i' '42' --el 'l' '1234567890123' --ed 'd' '3.25'"
        )
    }

    @Test
    fun `am start without launch arguments has no trailing space`() {
        assertThat(AndroidUserCommands.amStartCommand(17, "a.b/.Main", emptyMap())).isEqualTo(
            "am start --user 17 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10000000 -n a.b/.Main"
        )
    }

    @Test
    fun `shell quote escapes single quotes`() {
        assertThat(AndroidUserCommands.shellQuote("it's")).isEqualTo("'it'\\''s'")
    }

    @Test
    fun `missing double extra support is recognised from am's error`() {
        assertThat(
            AndroidUserCommands.isMissingDoubleExtraSupport(
                "Exception occurred while executing 'start':\njava.lang.IllegalArgumentException: Unknown option: --ed"
            )
        ).isTrue()
        assertThat(AndroidUserCommands.isMissingDoubleExtraSupport("Error: Activity not started")).isFalse()
        assertThat(AndroidUserCommands.isMissingDoubleExtraSupport(null)).isFalse()
    }
}
