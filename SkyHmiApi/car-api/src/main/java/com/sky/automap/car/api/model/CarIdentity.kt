package com.sky.automap.car.api.model

import android.os.Parcel
import android.os.Parcelable

/**
 * 服务端身份——跨进程契约里的「我是谁」。
 *
 * 为什么不用 @Parcelize：Parcel 的读写顺序本身就是协议的一部分。
 * 手写 [writeToParcel] 能让「新字段只能追加在末尾」这条规则在代码里看得见，
 * 而注解生成器靠属性声明顺序，一次无意的属性重排就会破坏与旧客户端的兼容，
 * 且编译期毫无提示。
 *
 * 字段兼容规则：只允许在末尾追加字段，不允许删除、改类型、调顺序。
 *
 * @param applicationId 实际包名（按车型覆盖，如 com.sky.automap.h6）
 * @param carModel 车型标识（h6 / h9），与 app-builds.json 的 carModels 一致
 * @param channelId 渠道 ID（APP_BUILD_ID），同车型多渠道时用于区分
 * @param mapVendor 地图厂商（amap / baidu），由车型决定，运行期不可切换
 */
class CarIdentity(
    val applicationId: String,
    val carModel: String,
    val channelId: String,
    val mapVendor: String,
) : Parcelable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(applicationId)
        dest.writeString(carModel)
        dest.writeString(channelId)
        dest.writeString(mapVendor)
    }

    override fun describeContents(): Int = 0

    override fun toString(): String =
        "CarIdentity(applicationId=$applicationId, carModel=$carModel, " +
            "channelId=$channelId, mapVendor=$mapVendor)"

    companion object {

        /**
         * AIDL 生成的读写代码按 `CarIdentity.CREATOR` 访问，
         * Kotlin 里必须用 @JvmField 才会生成真正的静态字段。
         */
        @JvmField
        val CREATOR: Parcelable.Creator<CarIdentity> = object : Parcelable.Creator<CarIdentity> {
            override fun createFromParcel(source: Parcel): CarIdentity = CarIdentity(
                applicationId = source.readString().orEmpty(),
                carModel = source.readString().orEmpty(),
                channelId = source.readString().orEmpty(),
                mapVendor = source.readString().orEmpty(),
            )

            override fun newArray(size: Int): Array<CarIdentity?> = arrayOfNulls(size)
        }
    }
}
