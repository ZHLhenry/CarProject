package com.sky.automap.car.api.model

import android.os.Parcel
import android.os.Parcelable

/**
 * POI 搜索结果（对外契约）。
 *
 * 经纬度**扁平**放在本类里而不是嵌一个 [CarLatLng]：少一层 parcelable 读写，
 * 也少一处「嵌套对象可能为 null」的判断——跨进程契约里越少可空字段越好。
 *
 * 字段兼容规则：只允许在末尾追加字段，不改顺序、不删字段。
 * 需要成组的扩展信息时，建议新加一个 Parcelable 并追加字段，而不是复用 name 拼串。
 */
class CarPoi(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
) : Parcelable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(name)
        dest.writeString(address)
        dest.writeDouble(latitude)
        dest.writeDouble(longitude)
    }

    override fun describeContents(): Int = 0

    override fun toString(): String =
        "CarPoi($id, $name, $address, $latitude, $longitude)"

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<CarPoi> = object : Parcelable.Creator<CarPoi> {
            override fun createFromParcel(source: Parcel): CarPoi = CarPoi(
                id = source.readString().orEmpty(),
                name = source.readString().orEmpty(),
                address = source.readString().orEmpty(),
                latitude = source.readDouble(),
                longitude = source.readDouble(),
            )

            override fun newArray(size: Int): Array<CarPoi?> = arrayOfNulls(size)
        }
    }
}
