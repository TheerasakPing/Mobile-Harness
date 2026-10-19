package com.jarves.mh.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteSshCommandTest {
    @Test fun strictHostVerificationRequiredAndNoPasswordEmbedded() {
        val profile = RemoteWorkspaceProfile("example.org", "developer", 2222,
            "/workspace/demo/.ssh/known_hosts")
        val command = RemoteSshCommand.connectionProbe(profile)
        assertTrue(command.contains("StrictHostKeyChecking=yes"))
        assertTrue(command.contains("BatchMode=yes"))
        assertTrue(command.contains("UserKnownHostsFile="))
        assertTrue(command.contains("2222"))
        assertTrue(command.contains("example.org"))
        assertTrue(command.endsWith(" 'pwd'"))
        assertFalse(command.contains("StrictHostKeyChecking=no"))
    }

    @Test fun shellInjectionAndTraversalAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteSshCommand.connectionProbe(RemoteWorkspaceProfile("example.org; rm -rf /", "u", 22,
                "/workspace/demo/.ssh/known_hosts"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteSshCommand.connectionProbe(RemoteWorkspaceProfile("example.org", "root", 22,
                "/workspace/../.ssh/known_hosts"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteSshCommand.connectionProbe(RemoteWorkspaceProfile("example.org", "developer", 0,
                "/workspace/demo/.ssh/known_hosts"))
        }
    }

    @Test fun userMustProvideKnownHostTrustFile() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteSshCommand.connectionProbe(RemoteWorkspaceProfile("example.org", "developer", 22, ""))
        }
    }
}
