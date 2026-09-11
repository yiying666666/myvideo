package com.example.myapplication.cover

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable

/**
 * 等价于 [Intent.getParcelableExtra]，但在 API 33+ 上不会触发废弃警告，
 * 同时保持对 minSdk 24 的兼容。
 */
inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }

/**
 * 等价于 [Bundle.getParcelable]，但在 API 33+ 上不会触发废弃警告，
 * 同时保持对 minSdk 24 的兼容。
 */
inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key) as? T
    }
