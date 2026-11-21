package com.sky.automap

import android.os.Bundle
import com.sky.automap.assemble.PlatformAssembler
import com.sky.mvvm.base.container.BaseContainerActivity

/**
 * 全工程唯一的 Activity（单 Activity + 多 Fragment）。
 *
 * 具体加载哪套 HMI 导航图，由壳工程按 Flavor 组装时注入的 [PlatformAssembler] 决定：
 * H6 包加载 HMI_H6 的导航图，H9 包加载 HMI_H9 的导航图。
 */
class MainActivity : BaseContainerActivity<MainViewModel>() {

    override fun navGraphId(): Int = PlatformAssembler.hmiEntry().navGraphId()

    override fun onContainerReady(savedInstanceState: Bundle?) {
        title = PlatformAssembler.hmiEntry().displayName
    }
}
