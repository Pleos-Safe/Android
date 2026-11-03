package io.issac.pleosandroid.core.navigation

import androidx.navigation3.runtime.NavKey

/** Screen identity. Each concrete route must be @Serializable for back-stack restoration. */
interface AppRoute : NavKey
