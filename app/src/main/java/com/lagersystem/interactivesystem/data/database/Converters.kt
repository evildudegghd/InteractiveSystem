package com.lagersystem.interactivesystem.data.database

import androidx.room.TypeConverter
import com.lagersystem.interactivesystem.data.log.ActionType
import com.lagersystem.interactivesystem.data.user.UserRole

class Converters {
    @TypeConverter
    fun fromActionType(value: ActionType) = value.name

    @TypeConverter
    fun toActionType(value: String) = ActionType.valueOf(value)

    @TypeConverter
    fun fromUserRole(value: UserRole) = value.name

    @TypeConverter
    fun toUserRole(value: String) = UserRole.valueOf(value)
}
