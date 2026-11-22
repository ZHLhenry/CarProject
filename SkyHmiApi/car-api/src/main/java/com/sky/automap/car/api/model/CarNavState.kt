package com.sky.automap.car.api.model

import android.os.Parcel
import android.os.Parcelable

/**
 * 导航状态快照（对外契约）。
 *
 * 约定：**永远不返回 null**，空闲态用 `navigating=false` 表示。
 * 让调用方少一个空判断，也让「服务端没实现」和「当前没导航」区分得开。
 *
 * 字段兼容规则：只允许在末尾追加字段。
 *
 * @param navigating 是否正在导航
 * @param destinationName 终点名称，空闲时为空串
 * @param remainingDistanceMeters 剩余距离（米），空闲时为 0
 * @param etaSeconds 预计到达剩余秒数，空闲时为 0
 */
class CarNavState(
    val navigating: Boolean,
    val destinationName: String,
    val remainingDistanceMeters: Int,
    val etaSeconds: Int,
) : Parcelable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        // Parcel 没有 writeBoolean，用 0/1 表示，读侧 != 0
        dest.writeInt(if (navigating) 1 else 0)
        dest.writeString(destinationName)
        dest.writeInt(remainingDistanceMeters)
        dest.writeInt(etaSeconds)
    }

    override fun describeContents(): Int = 0

    override fun toString(): String =
        "CarNavState(navigating=$navigating, destination=$destinationName, " +
            "remaining=${remainingDistanceMeters}m, eta=${etaSeconds}s)"

    companion object {

        /** 空闲态。避免各处 new 一个全零实例，也保证字段含义一致 */
        @JvmField
        val IDLE: CarNavState = CarNavState(false, "", 0, 0)

        @JvmField
        val CREATOR: Parcelable.Creator<CarNavState> = object : Parcelable.Creator<CarNavState> {
            override fun createFromParcel(source: Parcel): CarNavState = CarNavState(
                navigating = source.readInt() != 0,
                destinationName = source.readString().orEmpty(),
                remainingDistanceMeters = source.readInt(),
                etaSeconds = source.readInt(),
            )

            override fun newArray(size: Int): Array<CarNavState?> = arrayOfNulls(size)
        }
    }
}
