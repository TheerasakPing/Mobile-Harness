package com.jarves.mh.remote

/** No credentials, keys or network connections are stored by a profile. */
data class RemoteWorkspaceProfile(
    val host: String,
    val username: String,
    val port: Int,
    val knownHostsPath: String,
)

object RemoteSshCommand {
    private val hostname = Regex("[A-Za-z0-9][A-Za-z0-9.:-]{0,252}")
    private val login = Regex("[A-Za-z_][A-Za-z0-9_.-]{0,31}")
    private val knownHosts = Regex("/workspace/[A-Za-z0-9_./-]{1,256}")

    /** A read-only SSH connectivity probe to run in the existing Ubuntu project terminal. */
    fun connectionProbe(profile: RemoteWorkspaceProfile): String {
        require(hostname.matches(profile.host) && !profile.host.contains("..")) { "Unsafe SSH host" }
        require(login.matches(profile.username)) { "Unsafe SSH user" }
        require(profile.port in 1..65535) { "Port outside 1–65535" }
        require(knownHosts.matches(profile.knownHostsPath) &&
            profile.knownHostsPath.split('/').none { it == ".." || it == "." }) {
            "Provide a trusted known_hosts file inside /workspace/<project>/"
        }
        val argv = listOf(
            "ssh",
            "-o", "BatchMode=yes",
            "-o", "StrictHostKeyChecking=yes",
            "-o", "UserKnownHostsFile=${profile.knownHostsPath}",
            "-o", "ConnectTimeout=10",
            "-p", profile.port.toString(),
            "-l", profile.username,
            profile.host,
            "pwd",
        )
        return argv.joinToString(" ") { quote(it) }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
