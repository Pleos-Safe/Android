package io.issac.pleosandroid.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "album_print")
data class PrintEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "region") val packType: String?,
    val pool: String,
    val baseGrade: String,
    val art: String,
    val variantArt: String,
    val place: String,
    val serial: String,
    @ColumnInfo(defaultValue = "NULL") val mapRegionId: String? = null,
)

@Entity(tableName = "album_pack")
data class PackEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "region") val packType: String?,
    val source: String,
    val receivedAt: Long,
    val place: String? = null,
    val journeyId: String? = null,
)

@Entity(
    tableName = "album_copy",
    foreignKeys = [
        ForeignKey(PrintEntity::class, ["id"], ["printId"]),
        ForeignKey(PackEntity::class, ["id"], ["packId"]),
    ],
    indices = [Index("printId"), Index(value = ["packId"], unique = true)],
)
data class CopyEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val printId: String,
    val variant: Boolean,
    val level: Int,
    val highest: Int,
    val status: String,
    val revision: Long,
    val acquiredAt: Long,
    val seenAt: Long?,
    val legacyId: String? = null,
)

@Entity(tableName = "album_wallet")
data class WalletEntity(@PrimaryKey val id: Int = 1, val featuredId: String? = null)

@Entity(
    tableName = "album_action",
    foreignKeys = [ForeignKey(CopyEntity::class, ["id"], ["copyId"])],
    indices = [Index("copyId")],
)
data class ForgeEntity(
    @PrimaryKey val id: String,
    val copyId: String,
    val beforeLevel: Int,
    val afterLevel: Int,
    val outcome: String,
    val cost: Long,
    val at: Long,
    val shown: Boolean,
    val materialId: String?,
)
