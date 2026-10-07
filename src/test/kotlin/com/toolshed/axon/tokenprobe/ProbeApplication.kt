package com.toolshed.axon.tokenprobe

import com.toolshed.axon.ProcessorSegmentConfiguration
import com.toolshed.axon.TokenStoreConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile

/**
 * A minimal application for the foundation tests: only the probe package is scanned, plus the token-store and
 * segment configurations under test. Gated behind the `probes` profile so it never loads in the app or other tests.
 */
@SpringBootApplication(scanBasePackages = ["com.toolshed.axon.tokenprobe"])
@Import(TokenStoreConfiguration::class, ProcessorSegmentConfiguration::class)
@Profile("probes")
class ProbeApplication
