package com.gc.waravi.skyway

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils

class AudioOutputManager(
    private val context: Context,
    initialOutputMode: OutputMode = OutputMode.SPEAKER_PHONE,
    private var audioOutputType: OutputType = OutputType.SPEAKER_PHONE
) {
    private var isHeadsetReceiverRegistered = false
    private var eventListeners = mutableSetOf<OnEventListener>()

    private var audioFocusRequest: AudioFocusRequestCompat? = null

    private val bluetoothManager: BluetoothManager = BluetoothManager()
    private val audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    when (audioState.lastKnownFocusState) {
                        AudioManager.AUDIOFOCUS_LOSS -> {}
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {}
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> audioManager.setStreamVolume(
                            AudioManager.STREAM_VOICE_CALL,
                            audioState.lastStreamVolume, 0
                        )
                        else -> {

                        }
                    }
                    audioOutputType = audioState.lastOutputType
                    bluetoothManager.connectBluetooth()
                    bluetoothManager.forceInvokeConnectBluetooth()
                }
                AudioManager.AUDIOFOCUS_LOSS -> {}
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {}
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    audioState.lastStreamVolume =
                        audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0)
                }
                AudioManager.AUDIOFOCUS_NONE -> {
                }
                else -> {
                }
            }
            audioState.lastOutputType = audioOutputType
            audioState.lastKnownFocusState = focusChange
            eventListeners.forEach { it.onAudioFocusChange(focusChange) }
        }
    private val headsetBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isInitialStickyBroadcast) {
                if (Intent.ACTION_HEADSET_PLUG == intent.action) {
                    val isConnected = intent.getIntExtra(HEADSET_PLUG_STATE_KEY, 0) == 1
                    if (isConnected) {
                        audioState.lastOutputType = audioOutputType
                        audioOutputType = OutputType.HEAD_PHONES
                        setSpeakerphoneOn(false)
                        audioManager.isBluetoothScoOn = false
                    } else {
                        if (audioOutputType == OutputType.HEAD_PHONES) {
                            if (audioState.lastOutputType == OutputType.BLUETOOTH && Utils.isBluetoothOutput(context)) {
                                audioManager.isBluetoothScoOn = true
                                bluetoothManager.startBluetoothSco()
                                audioOutputType = OutputType.BLUETOOTH
                            } else {
                                if (audioState.lastOutputType == OutputType.SPEAKER_PHONE) {
                                    audioOutputType = OutputType.SPEAKER_PHONE
                                    setSpeakerphoneOn(true)
                                }
                                if (audioState.lastOutputType == OutputType.EAR_PIECE) {
                                    audioOutputType = OutputType.EAR_PIECE
                                    setSpeakerphoneOn(false)
                                }
                            }
                        }
                    }
                    eventListeners.forEach { it.onHeadphoneConnectionStateChange(isConnected) }
                }
            }
        }
    }

    private val speakerphoneStateChangedListeners = mutableSetOf<OnSpeakerphoneStateChangedListener>()

    val audioManager = context.getSystemService<AudioManager>()!!
    val audioState: AudioState = AudioState(initialOutputMode, audioOutputType)

    var isSpeakerphoneStateOn: Boolean = audioManager.isSpeakerphoneOn
        set(value) {
            if (field != value) {
                field = value
                speakerphoneStateChangedListeners.forEach {
                    it.onSpeakerphoneStateChanged(value)
                }
            }
        }

    val isWiredHeadsetOutput: Boolean
        get() = Utils.isWiredHeadsetOutput(context)

    val isBluetoothOutput: Boolean
        get() = Utils.isBluetoothOutput(context)

    @RequiresApi(Build.VERSION_CODES.S)
    fun setCommunicationDevice(deviceId: Int) : Boolean{
        Log.e("NQD", "setCommunicationDevice...${deviceId}")
        audioManager.availableCommunicationDevices.forEach {
            if (it.id == deviceId){
                return audioManager.setCommunicationDevice(it)
            }
        }
        return false
    }

    fun addSpeakerphoneStateChangedListener(listener: OnSpeakerphoneStateChangedListener) {
        speakerphoneStateChangedListeners.add(listener)
    }

    fun removeSpeakerphoneStateChangedListener(listener: OnSpeakerphoneStateChangedListener) {
        speakerphoneStateChangedListeners.remove(listener)
    }

    fun addEventListener(eventListener: OnEventListener) {
        this.eventListeners.add(eventListener)
    }

    fun removeEventListener(eventListener: OnEventListener) {
        this.eventListeners.remove(eventListener)
    }

    fun requestAudioFocus(usage: Int, contentType: Int, durationHint: Int): Int {
        abandonAudioFocus()
        return AudioManagerCompat.requestAudioFocus(
            audioManager,
            AudioFocusRequestCompat.Builder(durationHint)
                .setAudioAttributes(
                    AudioAttributesCompat.Builder()
                        .setUsage(usage)
                        .setContentType(contentType)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
                .also {
                    audioFocusRequest = it
                }
        )
    }

    fun abandonAudioFocus(): Int {
        return audioFocusRequest?.let {
            audioFocusRequest = null
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
        } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
    }

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    private fun registerHeadsetReceiver() {
        if (isHeadsetReceiverRegistered) {
            return
        }
        context.registerReceiver(headsetBroadcastReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        isHeadsetReceiverRegistered = true
    }

    private fun unregisterHeadsetReceiver() {
        if (!isHeadsetReceiverRegistered) {
            return
        }
        context.unregisterReceiver(headsetBroadcastReceiver)
        isHeadsetReceiverRegistered = false
    }

    fun setOutputMode(mode: OutputMode) {
        audioState.lastOutputMode = mode
        val outputMode = if (mode == OutputMode.SPEAKER_PHONE) {
            OutputMode.SPEAKER_PHONE
        } else {
            OutputMode.HANDSET
        }
        if (OutputMode.SPEAKER_PHONE == outputMode) {
            audioState.lastOutputType = OutputType.SPEAKER_PHONE
            audioOutputType = OutputType.SPEAKER_PHONE
            bluetoothManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            setSpeakerphoneOn(true)
        } else {
            audioState.lastOutputType = OutputType.EAR_PIECE
            if (Utils.isBluetoothOutput(context)) {
                setSpeakerphoneOn(false)
                bluetoothManager.connectBluetooth()
                audioOutputType = OutputType.BLUETOOTH
            } else if (Utils.isWiredHeadsetOutput(context)) {
                setSpeakerphoneOn(false)
                bluetoothManager.stopBluetoothSco()
                audioOutputType = OutputType.HEAD_PHONES
            } else {
                audioOutputType = OutputType.EAR_PIECE
                setSpeakerphoneOn(false)
                bluetoothManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
        }
    }

    fun changeOutputType(type: OutputType, mode: Int = AudioManager.MODE_IN_COMMUNICATION) {
        Toast.makeText(context, "Change output type: ${type.name}", Toast.LENGTH_SHORT).show()
        when (type) {
            OutputType.SPEAKER_PHONE -> {
                audioManager.mode = mode
                audioState.lastOutputMode = OutputMode.SPEAKER_PHONE
                audioOutputType = OutputType.SPEAKER_PHONE
                audioState.lastOutputType = OutputType.SPEAKER_PHONE
                bluetoothManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                setSpeakerphoneOn(true)
            }

            OutputType.EAR_PIECE -> {
                audioManager.mode = mode
                audioState.lastOutputMode = OutputMode.HANDSET
                audioOutputType = OutputType.EAR_PIECE
                audioState.lastOutputType = OutputType.EAR_PIECE
                bluetoothManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                setSpeakerphoneOn(false)
            }

            OutputType.BLUETOOTH -> {
                audioState.lastOutputMode = OutputMode.HANDSET
                if (Utils.isBluetoothOutput(context)) {
                    audioManager.mode = mode
                    setSpeakerphoneOn(false)
                    bluetoothManager.connectBluetooth()
                    audioOutputType = OutputType.BLUETOOTH
                    audioState.lastOutputType = OutputType.BLUETOOTH
                }
            }

            OutputType.HEAD_PHONES -> {
                audioState.lastOutputMode = OutputMode.HANDSET
                if (Utils.isWiredHeadsetOutput(context)) {
                    audioManager.mode = mode
                    bluetoothManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    setSpeakerphoneOn(false)
                    audioOutputType = OutputType.HEAD_PHONES
                    audioState.lastOutputType = OutputType.HEAD_PHONES
                } else {
                    audioManager.mode = mode
                    bluetoothManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    setSpeakerphoneOn(false)
                    audioOutputType = OutputType.EAR_PIECE
                    audioState.lastOutputType = OutputType.EAR_PIECE
                }
            }
        }
    }

    fun autoPickOutputType(preferType: OutputType = OutputType.EAR_PIECE) {
        val type = when {
            isBluetoothOutput -> OutputType.BLUETOOTH
            isWiredHeadsetOutput -> OutputType.HEAD_PHONES
            else -> preferType
        }
        changeOutputType(type)
    }

    fun init() {
        bluetoothManager.bluetoothState = BluetoothState.Disconnected
        bluetoothManager.enableBluetoothEvents()
    }

    fun start() {
        bluetoothManager.registerBtReceiver()
        registerHeadsetReceiver()
    }

    fun resume() {
        if (bluetoothManager.bluetoothState == BluetoothState.Disconnected) {
            if (audioState.lastOutputType == OutputType.SPEAKER_PHONE) {
                if (!Utils.isWiredHeadsetOutput(context)) {
                    setSpeakerphoneOn(true)
                }
            }
        }

        /* register handler for phonejack notifications */
        bluetoothManager.registerBtReceiver()
        registerHeadsetReceiver()
//        bluetoothManager.connectBluetooth()
//        bluetoothManager.forceInvokeConnectBluetooth()
    }

    fun resetOutputType() {
        audioState.lastOutputType = OutputType.SPEAKER_PHONE
        audioOutputType = OutputType.SPEAKER_PHONE
        bluetoothManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerphoneOn(false)
    }

    fun destroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        bluetoothManager.disableBluetoothEvents()
        unregisterHeadsetReceiver()
        audioManager.mode = AudioManager.MODE_NORMAL
        setSpeakerphoneOn(false)
        abandonAudioFocus()
        eventListeners.clear()
    }

    fun checkAudioOutput(){
        val connectedBluetoothDeviceName = Utils.getBluetoothOutputName(context)
        val isSpeakerOn = audioManager.isSpeakerphoneOn
        if (isSpeakerOn.not() && connectedBluetoothDeviceName.isNullOrEmpty().not()){
            changeOutputType(
                OutputType.BLUETOOTH,
                if (Utils.isRunningOnTV(context)) AudioManager.MODE_NORMAL
                else AudioManager.MODE_IN_COMMUNICATION
            )
        } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            val savedDeviceInfo = PrefUtils.getAudioOutputDevice(context)
            if (savedDeviceInfo.isNotEmpty()){
                val selectedDeviceName = savedDeviceInfo.split("::").firstOrNull()
                val selectedDeviceType = savedDeviceInfo.split("::").lastOrNull()?.toInt()

                val selectedDevice = audioManager.availableCommunicationDevices.firstOrNull {
                    it.productName == selectedDeviceName && it.type == selectedDeviceType }

                if(selectedDevice != null){
                    setCommunicationDevice(selectedDevice.id)
                }
            } else{
                audioManager.availableCommunicationDevices.find { it.type == AudioDeviceInfo.TYPE_HDMI }?.let {
                    setAudioOutputDevice(it.id)
                }
            }
        }
    }

    fun setAudioOutputDevice(deviceId: Int){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            audioManager.availableCommunicationDevices.firstOrNull { it.id == deviceId }?.let {
                audioManager.setCommunicationDevice(it)
                PrefUtils.settAudioOutputDevice(context, "${it.productName}::${it.type}")
            }
        }
    }

    interface OnEventListener {
        fun onAudioFocusChange(focusChange: Int)
        fun onBluetoothScoAudioStateUpdated(state: Int)
        fun onHeadphoneConnectionStateChange(isConnected: Boolean)
    }

    fun interface OnSpeakerphoneStateChangedListener {
        fun onSpeakerphoneStateChanged(isOn: Boolean)
    }

    class AudioState @JvmOverloads constructor(
        var lastOutputMode: OutputMode = OutputMode.SPEAKER_PHONE,
        var lastOutputType: OutputType = OutputType.SPEAKER_PHONE
    ) {
        var lastStreamVolume = 0
        var lastKnownFocusState = 0

    }

    internal inner class BluetoothManager internal constructor() {
        private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        private val bluetoothLock = Any()
        private var isBluetoothHeadSetReceiverRegistered = false
        var bluetoothState: BluetoothState? = null
        private var bluetoothProfile: BluetoothProfile? = null

        private val bluetoothBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED == action) {
                    when (intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)) {
                        BluetoothHeadset.STATE_CONNECTED -> {
                            Handler().postDelayed(
                                { connectBluetooth() },
                                DEFAULT_BLUETOOTH_SCO_START_DELAY.toLong()
                            )
                        }
                        BluetoothHeadset.STATE_DISCONNECTING -> {
                        }
                        BluetoothHeadset.STATE_DISCONNECTED -> {
                            stopBluetoothSco()
                            audioManager.isBluetoothScoOn = false
                        }
                        else -> {}
                    }
                } else if (null != action && action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                    when (state) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            bluetoothState = BluetoothState.Connected
                            audioOutputType = OutputType.BLUETOOTH
                            audioManager.isBluetoothScoOn = true
                        }
                        AudioManager.SCO_AUDIO_STATE_ERROR -> {
                        }
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                            //restoreAudioAfterBluetoothDisconnect()
                            bluetoothState = BluetoothState.Disconnected
                            audioManager.isBluetoothScoOn = false
                            if (audioState.lastOutputType == OutputType.SPEAKER_PHONE) {
                                audioOutputType = OutputType.SPEAKER_PHONE
                                setSpeakerphoneOn(true)
                            }
                            if (audioState.lastOutputType == OutputType.EAR_PIECE) {
                                audioOutputType = OutputType.EAR_PIECE
                                setSpeakerphoneOn(false)
                            }
                        }
                        AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                        }
                        else -> {}
                    }
                    eventListeners.forEach { it.onBluetoothScoAudioStateUpdated(state) }
                }
            }
        }

        private val bluetoothProfileServiceListener: BluetoothProfile.ServiceListener =
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(type: Int, profile: BluetoothProfile) {
                    if (hasBluetoothPermission()) {
                        if (BluetoothProfile.HEADSET == type) {
                            bluetoothProfile = profile
                            val devices = profile.connectedDevices
                            if (!devices.isEmpty() &&
                                BluetoothHeadset.STATE_CONNECTED == profile.getConnectionState(
                                    devices[0]
                                )
                            ) {
                                val intent =
                                    Intent(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                                intent.putExtra(
                                    BluetoothHeadset.EXTRA_STATE,
                                    BluetoothHeadset.STATE_CONNECTED
                                )
                                bluetoothBroadcastReceiver.onReceive(context, intent)
                            }
                        }
                    }
                }

                override fun onServiceDisconnected(type: Int) {
                    if (type == BluetoothProfile.HEADSET) {
                        bluetoothProfile = null
                    }
                }
            }

        private val bluetoothHeadsetReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED == action) {
                    val state = intent.getIntExtra(
                        BluetoothHeadset.EXTRA_STATE, -1
                    )
                    when (state) {
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED -> {}
                        BluetoothHeadset.STATE_AUDIO_CONNECTING -> {}
                        BluetoothHeadset.STATE_AUDIO_CONNECTED -> {}
                        else -> {}
                    }
                }
            }
        }

        fun forceInvokeConnectBluetooth() {

            synchronized(bluetoothLock) {
                bluetoothState = BluetoothState.Disconnected
                bluetoothAdapter?.getProfileProxy(
                    context,
                    bluetoothProfileServiceListener,
                    BluetoothProfile.HEADSET
                )
            }
        }

        fun pickSpeakerphoneState(outputType: OutputType) {
            synchronized(bluetoothLock) {
                if (BluetoothState.Connected != bluetoothState) {
                    if (Utils.isWiredHeadsetOutput(context)) {
                        setSpeakerphoneOn(false)
                    } else {
                        if (outputType == OutputType.SPEAKER_PHONE) {
                            setSpeakerphoneOn(true)
                        }
                    }
                }
            }
        }

        fun registerBtReceiver() {
            if (isBluetoothHeadSetReceiverRegistered) {
                return
            }
            val btFilter = IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            btFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            context.registerReceiver(bluetoothBroadcastReceiver, btFilter)

            context.registerReceiver(
                bluetoothHeadsetReceiver,
                IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
            )
            isBluetoothHeadSetReceiverRegistered = true
        }

        fun unregisterBtReceiver() {
            if (!isBluetoothHeadSetReceiverRegistered) {
                return
            }
            context.unregisterReceiver(bluetoothBroadcastReceiver)
            context.unregisterReceiver(bluetoothHeadsetReceiver)
            isBluetoothHeadSetReceiverRegistered = false
        }

        fun startBluetoothSco() {
            try {
                if(!audioManager.isBluetoothScoOn){
                    audioManager.isBluetoothScoOn = true
                    audioManager.startBluetoothSco()
                }
            } catch (e: RuntimeException) {
                Log.e(this::class.simpleName, "Start bluetooth error: ${e.message}")
            }
        }

        fun enableBluetoothEvents() {
            if (audioManager.isBluetoothScoAvailableOffCall) {
                registerBtReceiver()
                connectBluetooth()
            }
        }

        fun connectBluetooth() {
//            audioManager.isBluetoothScoOn = true
            startBluetoothSco()
        }

        fun stopBluetoothSco() {
            try {
                audioManager.stopBluetoothSco()
            } catch (e: RuntimeException) {
                Log.e(this::class.simpleName, "Stop bluetooth error: ${e.message}")
            }
        }

        fun disableBluetoothEvents() {
            if (null != bluetoothProfile && bluetoothAdapter != null) {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothProfile)
            }
            unregisterBtReceiver()

            val intent = Intent(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            intent.putExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED)
            bluetoothBroadcastReceiver.onReceive(context, intent)
        }
    }

    private fun setSpeakerphoneOn(isOn: Boolean) {
        audioManager.isSpeakerphoneOn = isOn
        isSpeakerphoneStateOn = isOn
    }

    fun getSpeakerOn(): Boolean = audioManager.isSpeakerphoneOn
    fun getOutputType(): OutputType = audioOutputType

    enum class OutputMode {
        SPEAKER_PHONE, HANDSET
    }

    enum class OutputType {
        SPEAKER_PHONE, EAR_PIECE, HEAD_PHONES, BLUETOOTH
    }

    enum class BluetoothState {
        Connected,
        Disconnected
    }

    companion object {

        private const val HEADSET_PLUG_STATE_KEY = "state"
        private const val DEFAULT_BLUETOOTH_SCO_START_DELAY = 2000
    }
}