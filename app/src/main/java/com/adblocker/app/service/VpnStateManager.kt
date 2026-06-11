package com.adblocker.app.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object VpnStateManager {
    enum class VpnState { IDLE, STARTING, RUNNING, STOPPING, ERROR }

    private val _state = MutableLiveData(VpnState.IDLE)
    val state: LiveData<VpnState> = _state

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var pendingStart = false

    fun prepareAndStart(activity: Activity): Boolean {
        val prepareIntent = VpnService.prepare(activity)
        return if (prepareIntent != null) {
            pendingStart = true
            activity.startActivityForResult(prepareIntent, 1)
            false
        } else {
            startService(activity)
            true
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, context: Context) {
        if (requestCode != 1) return
        pendingStart = false
        if (resultCode == Activity.RESULT_OK) {
            startService(context)
        } else {
            _state.postValue(VpnState.IDLE)
        }
    }

    fun start(context: Context) {
        if (_state.value == VpnState.RUNNING || _state.value == VpnState.STARTING) return
        _state.value = VpnState.STARTING
        startService(context)
    }

    private fun startService(context: Context) {
        val intent = Intent(context, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        if (_state.value != VpnState.RUNNING) return
        _state.value = VpnState.STOPPING
        val intent = Intent(context, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun onVpnRunning() { _state.postValue(VpnState.RUNNING) }
    fun onVpnStopped() { _state.postValue(VpnState.IDLE) }
    fun onVpnError(msg: String) {
        _state.postValue(VpnState.ERROR)
        _errorMessage.postValue(msg)
    }
}
