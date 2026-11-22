package com.sky.automap.car.service

import com.sky.automap.car.api.model.CarLatLng
import com.sky.automap.car.api.model.CarPoi
import com.sky.automap.map.api.LatLng
import com.sky.automap.map.api.MapPoi

/**
 * 跨进程 DTO（`car-api`）↔ 领域模型（`map-api`）之间的唯一转换点。
 *
 * 为什么必须集中在这一层：
 * - `car-api` 是对外承诺，字段不能跟着内部模型改；
 * - `map-api` 是内部契约，重构时不应该波及到外部调用方。
 * 两边各自演进，只有这里需要跟着改。
 *
 * 全部声明为 internal：转换细节不对外暴露（本模块也不发布 AAR）。
 */

internal fun LatLng.toCarLatLng(): CarLatLng = CarLatLng(latitude, longitude)

internal fun CarLatLng.toLatLng(): LatLng = LatLng(latitude, longitude)

internal fun MapPoi.toCarPoi(): CarPoi = CarPoi(
    id = id,
    name = name,
    address = address,
    latitude = position.latitude,
    longitude = position.longitude,
)
