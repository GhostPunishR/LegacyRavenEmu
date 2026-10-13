package com.ravenemu.v3.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Public Kotlin/Native entry point used by the SwiftUI host. */
fun MainViewController(): UIViewController = ComposeUIViewController { RavenEmuApp() }
