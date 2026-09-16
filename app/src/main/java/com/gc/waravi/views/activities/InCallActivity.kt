package com.gc.waravi.views.activities

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.gc.waravi.R
import com.gc.waravi.base.BaseActivity
import com.gc.waravi.base.BaseApplication
import com.gc.waravi.views.viewmodels.InCallViewModelFactory

class InCallActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_call)
        val navHostFragment = (supportFragmentManager.findFragmentById(R.id.navFragment) as NavHostFragment)
        val inflater = navHostFragment.navController.navInflater
        val graph = inflater.inflate(R.navigation.incall_graph)
        navHostFragment.navController.setGraph(graph, intent.extras)
    }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = InCallViewModelFactory((application as BaseApplication).repository)

}