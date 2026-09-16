package com.gc.waravi.views.adapters

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.FragmentStatePagerAdapter

class HomePagerAdapter(fm: FragmentManager) :
    FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT)  {
    private val fragments = mutableListOf<Fragment>()

    override fun getCount(): Int {
        return fragments.size
    }

    override fun getItem(position: Int): Fragment {
        return fragments[position]
    }

    fun addFragment(fragment: Fragment){
        fragments.add(fragment)
    }

//    override fun getItemPosition(`object`: Any): Int {
//        return POSITION_NONE
//    }

}