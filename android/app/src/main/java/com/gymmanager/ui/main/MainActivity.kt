package com.gymmanager.ui.main

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.gymmanager.R
import com.gymmanager.databinding.ActivityMainBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.auth.LoginActivity
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfig: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // All bottom-nav destinations are top-level (no Up arrow)
        appBarConfig = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.membersFragment,
                R.id.attendanceFragment,
                R.id.paymentsFragment,
                R.id.masterFragment,
            )
        )

        setupActionBarWithNavController(navController, appBarConfig)
        binding.bottomNavigation.setupWithNavController(navController)

        // Eagerly fetch settings: keep currency + logo always up-to-date.
        lifecycleScope.launch {
            val result = gymApp.settingsRepository.getSettings()
            if (result is NetworkResult.Success) {
                val data = result.data
                gymApp.tokenManager.saveCurrencySymbol(data.currencySymbol)
                // Cache logo to disk, then show in toolbar
                gymApp.logoCache.save(this@MainActivity, data.logoBase64)
                applyToolbarLogo()
            }
        }
        // Also show any already-cached logo immediately (avoids blank toolbar on re-launch)
        applyToolbarLogo()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logout) {
            logout()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()

    /** Loads the cached gym logo and sets it as the Toolbar logo (left of title). */
    fun applyToolbarLogo() {
        val bmp = gymApp.logoCache.load(this) ?: return
        // Scale to ~40dp for the toolbar
        val sizePx = (40 * resources.displayMetrics.density).toInt()
        val scaled  = android.graphics.Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
        binding.toolbar.logo = BitmapDrawable(resources, scaled)
    }

    private fun logout() {
        gymApp.authRepository.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
