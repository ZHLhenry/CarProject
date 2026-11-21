package com.sky.automap.hmi.h9

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sky.mvvm.base.viewmodel.BaseViewModel

/**
 * H9 首页 ViewModel。
 */
class HomeH9ViewModel : BaseViewModel() {

    private val _status = MutableLiveData("H9 HMI 已装配业务组件：poi / navigation / route")
    val status: LiveData<String> = _status

    fun refreshStatus(withMapHint: String) {
        _status.value = "H9 HMI · $withMapHint"
    }
}
