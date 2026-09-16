package com.gc.waravi.views.fragments

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.leanback.tab.LeanbackTabLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gc.waravi.R
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentMainBinding
import com.gc.waravi.skyway.SkywayManager
import com.gc.waravi.skyway.call.ARG_CALL_ACTION
import com.gc.waravi.skyway.call.ARG_CALL_ID
import com.gc.waravi.skyway.call.CallManager
import com.gc.waravi.skyway.call.CallType
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils
import com.gc.waravi.views.activities.HomeActivity
import com.gc.waravi.views.activities.InCallActivity
import com.gc.waravi.views.adapters.HomePagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainFragment : BaseFragment<FragmentMainBinding>() {
    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMainBinding {
        return FragmentMainBinding.inflate(inflater, container,false)
    }

    private val tabData = arrayOf(
        Triple(RecentFragment(), R.string.lbl_history, R.drawable.toptab_history_icn),
        Triple(ShortListFragment(), R.string.lbl_short, R.drawable.toptab_tansyuku_icn),
        Triple(KeypadFragment.newInstance(KeypadType.P2PCall), R.string.lbl_keypad, R.drawable.toptab_key_icn),
        Triple(KeypadFragment.newInstance(KeypadType.ROOM), R.string.label_group, R.drawable.toptab_group_icn)
    )
    private lateinit var sectionsPagerAdapter : HomePagerAdapter

    override fun initViews() {
        CallManager.isInRoom = false
        this.setupBackButton()
        this.setupTabWithViewpager()
        viewModel.currentPeerId.observe(viewLifecycleOwner){
            binding.tvId.text = String.format("ID: %s", it)
        }
//        binding.tvId.text = String.format("ID: %s", viewModel.currentPeerId.value)
        binding.btnResettingId.setOnClickListener {
            findNavController().navigate(R.id.register_new_id)
        }
        binding.btnSetting.setOnClickListener {
            findNavController().navigate(R.id.showSetting)
        }
        binding.imvConnectionStatus.setImageLevel(3)
        this.registerCallAction()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.checkAndStartCallRegister()
        val lastTabIndex = PrefUtils.getLastTabIndex(requireContext())
        val defaultTabIndex = if(lastTabIndex >= 0 && lastTabIndex < tabData.size) lastTabIndex else 2
        if (requireActivity() is HomeActivity){
            (requireActivity() as HomeActivity).checkAccessibility()
        }

        val tabs = binding.tabs
        lifecycleScope.launch(Dispatchers.Main) {
            for(i in tabData.indices){
                sectionsPagerAdapter.addFragment(tabData[i].first)
                sectionsPagerAdapter.notifyDataSetChanged()
                if(i == defaultTabIndex){
                    binding.viewPager.currentItem = i
                }
                delay(100L)
            }

            binding.viewPager.visibility = View.VISIBLE

            tabs.visibility = View.VISIBLE
            binding.fakeTabs.visibility = View.GONE

            this@MainFragment.setUpTabs()

            //init tabs
            for (i in 0 until tabs.tabCount){
                tabs.getTabAt(i)?.text = getString(tabData[i].second)
                tabs.getTabAt(i)?.icon = ContextCompat.getDrawable(tabs.context, tabData[i].third)
                if (defaultTabIndex == i){
                    tabs.getTabAt(i)?.select()
                    tabs.getTabAt(i)?.view?.requestFocus()
                }
            }
        }
    }

    private fun setUpTabs(){
        val tabs: LeanbackTabLayout = binding.tabs
        tabs.setSelectedTabIndicatorColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
        tabs.setupWithViewPager(binding.viewPager)
        tabs.addOnTabSelectedListener(object : OnTabSelectedListener{
            override fun onTabSelected(tab: TabLayout.Tab) {
                PrefUtils.saveLastTabIndex(requireContext(), tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }

        })
    }



    private fun registerCallAction() {
        disposable.add(viewModel.callAction.subscribe({ peerId ->
            makeCall(peerId)
        },{
            Log.e(this::class.simpleName, it.message.toString())
        }))
        disposable.add(viewModel.roomAction.subscribe({ roomId ->
            joinRoom(roomId)
        },{
            Log.e(this::class.simpleName, it.message.toString())
        }))
    }

    private fun joinRoom(roomId: String){
        if (roomId.isNotEmpty()){
            val bundle = bundleOf(DefaultRoomMeetingFragment.ARG_GROUP_ID to roomId)
            findNavController().navigate(R.id.joinGroup, args = bundle)
        } else{
            showSnackBar(R.string.msg_empty_number)
        }
    }

    private fun makeCall(number: String){
        if(number.isNotEmpty()){
            val currentId = viewModel.currentPeerId.value
            if(number != currentId){
                viewModel.clearCallInput()
                val bundle = bundleOf(
                    ARG_CALL_ID to number,
                    ARG_CALL_ACTION to CallType.OUTGOING_CALL)
                startActivity(Intent(requireContext(), InCallActivity::class.java).apply {
                    putExtras(bundle)
                })
            } else{
                showSnackBar(R.string.msg_call_failure)
            }
        } else{
            showSnackBar(R.string.msg_empty_number)
        }
    }

    private fun setupBackButton(){
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.msg_exit_confirmation))
                .setPositiveButton(getString(R.string.btn_ok)
                ) { p0, _ ->
                    requireActivity().finishAffinity()
                    p0.dismiss()
                }
                .setNeutralButton(getString(R.string.btn_restart))
                { p0, _ ->
                    p0.dismiss()
                    if(requireActivity() is HomeActivity){
                        (requireActivity() as HomeActivity).restart()
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel)
                ) { p0, _ ->
                    p0.dismiss()
                }

            dialog.show().getButton(DialogInterface.BUTTON_POSITIVE).requestFocus()
        }
    }

    private fun setupTabWithViewpager(){
        sectionsPagerAdapter = HomePagerAdapter(childFragmentManager)
        val viewPager = binding.viewPager
        viewPager.adapter = sectionsPagerAdapter
        viewPager.offscreenPageLimit = 4
        viewPager.setKeyEventsEnabled(true)
        viewPager.visibility = View.INVISIBLE

        //init tabs
        val fakeTabs = binding.fakeTabs
        val lastTabIndex = PrefUtils.getLastTabIndex(requireContext())
        val defaultTabIndex = if(lastTabIndex >= 0 && lastTabIndex < tabData.size) lastTabIndex else 2
        for (i in tabData.indices){
            val tab = fakeTabs.newTab()
            tab.text = getString(tabData[i].second)
            tab.icon = ContextCompat.getDrawable(requireContext(), tabData[i].third)
            fakeTabs.addTab(tab)
            if (defaultTabIndex == i){
                tab.select()
                tab.view.requestFocus()
            }
        }
    }

    override fun onLongKeyEvent(keyEvent: KeyEvent){
        when(keyEvent.keyCode){
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                showRestartConfirmDialog()
            }
        }
    }

    private var isDialogShowing = false
    private fun showRestartConfirmDialog(){
        if(isDialogShowing) return
        isDialogShowing = true
        val dialog = Utils.createDialog(requireContext(), getString(R.string.msg_restart_confirmation)){
            if(requireActivity() is HomeActivity){
                (requireActivity() as HomeActivity).restart()
            }
        }
        dialog.setOnDismissListener {
            isDialogShowing = false
        }
        dialog.show().getButton(DialogInterface.BUTTON_POSITIVE).requestFocus()
    }
    


    private fun checkAndStartCallRegister(){
        if (viewModel.currentPeerId.value.isNullOrEmpty()){
            val lastId = SkywayManager.selfId.ifEmpty { PrefUtils.getLastId(requireContext()) }
            viewModel.setCurrentPeerId(lastId)
            CallManager.reconnectSocket()
        }
    }

}