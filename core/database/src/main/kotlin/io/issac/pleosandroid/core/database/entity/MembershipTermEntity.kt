package io.issac.pleosandroid.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "membership_term")
data class MembershipTermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endsAt: Long?,
    val revision: Long,
)
