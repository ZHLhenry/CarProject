package com.sky.automap.car.api.model

import android.os.Parcel
import android.os.Parcelable

/**
 * 经纬度（对外契约）。
 *
 * 为什么不用 `map-api` 里现成的 `LatLng`：契约 AAR 必须零依赖，
 * 一旦引用 `com.sky.automap:map-api`，外部方就被迫跟着升级地图适配层。
 * 跨进程边界的类型宁可重复定义一份，也不要跨层共享。
 *
 * 坐标参考系由服务端决定（高德 GCJ-02 / 百度 BD-09），契约不做转换，
 * 调用方只需原样回传给服务端。
 *
 * 字段兼容规则：只允许在末尾追加字段。
 */
class CarLatLng(
    val latitude: Double,
    val longitude: Double,
) : Parcelable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeDouble(latitude)
        dest.writeDouble(longitude)
    }

    override fun describeContents(): Int = 0

    override fun toString(): String = "CarLatLng($latitude, $longitude)"

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<CarLatLng> = object : Parcelable.Creator<CarLatLng> {
            override fun createFromParcel(source: Parcel): CarLatLng = CarLatLng(
                latitude = source.readDouble(),
                longitude = source.readDouble(),
            )

            override fun newArray(size: Int): Array<CarLatLng?> = arrayOfNulls(size)
        }
    }
}
