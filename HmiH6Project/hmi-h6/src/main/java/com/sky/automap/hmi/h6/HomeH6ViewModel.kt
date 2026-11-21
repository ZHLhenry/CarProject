package com.sky.automap.hmi.h6

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sky.mvvm.base.viewmodel.BaseViewModel

/**
 * H6 首页 ViewModel。
 *
 * 这里不拼任何面向用户的文案：状态文字由 Fragment 用 `getString(R.string.h6_home_*)` 取，
 * 这样多语言切换时跟随系统语言走资源，ViewModel 不持有 Context 也不需要重建。
 */
class HomeH6ViewModel : BaseViewModel() {

    private val _status = MutableLiveData<String>()
    val status: LiveData<String> = _status

    fun refreshStatus(text: String) {
        _status.value = text
    }
}
