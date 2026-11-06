/*
 * Copyright 2025 Kyant0, Apache License 2.0
 */
package com.nodeplayer.ui.component.backdrop

import android.os.Build

actual fun isRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

actual fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
