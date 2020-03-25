package com.coditory.gradle.manifest

import com.coditory.gradle.manifest.HostNameResolver.Companion.INET_HOST_NAME_RESOLVER
import java.time.Clock

open class ManifestPlugin : ParameterizedManifestPlugin(
    clock = Clock.systemUTC(),
    hostNameResolver = INET_HOST_NAME_RESOLVER
) {
    companion object {
        const val PLUGIN_ID = "com.coditory.manifest"
        const val GENERATE_MANIFEST_TASK = "manifest"
    }
}
