package com.example.myapplication.cover

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable

/**
 * [Intent.getParcelableExtra] without the deprecation warning on API 33+,
 * while staying compatible down to minSdk 24.
 */
inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }

/**
 * [Bundle.getParcelable] without the deprecation warning on API 33+,
 * while staying compatible down to minSdk 24.
 */
inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key) as? T
    }
