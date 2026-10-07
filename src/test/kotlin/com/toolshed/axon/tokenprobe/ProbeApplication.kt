package com.toolshed.axon.tokenprobe

import org.springframework.boot.autoconfigure.SpringBootApplication

/** A minimal application for the token-store test: the foundation package and the probe are scanned, nothing else. */
@SpringBootApplication(scanBasePackages = ["com.toolshed.axon"])
class ProbeApplication
