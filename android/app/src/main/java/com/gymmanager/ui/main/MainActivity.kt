package com.gymmanager.ui.main

import android.content.Intent
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

        // Eagerly fetch settings so the currency symbol is always up-to-date
        // (even for users who have never opened the Settings page in this session).
        lifecycleScope.launch {
            val result = gymApp.settingsRepository.getSettings()
            if (result is NetworkResult.Success) {
                gymApp.tokenManager.saveCurrencySymbol(result.data.currencySymbol)
            }
        }
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

    private fun logout() {
        gymApp.authRepository.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
