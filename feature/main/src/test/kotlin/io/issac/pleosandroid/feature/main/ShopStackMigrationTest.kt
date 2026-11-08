package io.issac.pleosandroid.feature.main

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.MembershipSettingsRoute
import io.issac.pleosandroid.core.navigation.RecordingSettingsRoute
import io.issac.pleosandroid.core.navigation.SettingsRoute
import io.issac.pleosandroid.core.navigation.ShopRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class ShopStackMigrationTest {
    @Test fun restoresTheActiveMembershipScreenUnderTheNewShopRoot() {
        val home = NavBackStack<NavKey>(MainRoute, SettingsRoute, ShopRoute, MembershipSettingsRoute)
        val shop = NavBackStack<NavKey>(ShopRoute)
        val stacks = mapOf(PleosTab.Travel to home, PleosTab.Shop to shop)
        assertEquals(PleosTab.Shop, migrateShopStack(PleosTab.Travel, stacks))
        assertEquals(listOf(MainRoute, SettingsRoute), home.toList())
        assertEquals(listOf(ShopRoute, MembershipSettingsRoute), shop.toList())
        shop.add(RecordingSettingsRoute)
        assertEquals(PleosTab.Shop, migrateShopStack(PleosTab.Shop, stacks))
        assertEquals(listOf(ShopRoute, MembershipSettingsRoute, RecordingSettingsRoute), shop.toList())
    }
}
