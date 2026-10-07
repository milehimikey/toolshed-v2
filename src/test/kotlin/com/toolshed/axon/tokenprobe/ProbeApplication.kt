package com.toolshed.axon.tokenprobe

import com.toolshed.axon.TokenStoreConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile

/**
 * A minimal application for the token-store test: only the probe package is scanned, plus the token-store
 * configuration under test. Gated behind the `probes` profile so it never loads in the app or other tests.
 */
@SpringBootApplication(scanBasePackages = ["com.toolshed.axon.tokenprobe"])
@Import(TokenStoreConfiguration::class)
@Profile("probes")
class ProbeApplication
