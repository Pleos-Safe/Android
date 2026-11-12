package io.issac.pleosandroid.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import io.issac.pleosandroid.core.database.entity.CopyEntity
import io.issac.pleosandroid.core.database.entity.ForgeEntity
import io.issac.pleosandroid.core.database.entity.PackEntity
import io.issac.pleosandroid.core.database.entity.PrintEntity
import io.issac.pleosandroid.core.database.entity.WalletEntity

@Dao
interface AlbumDao {
    @Query(
        "SELECT DISTINCT p.id AS placeId, c.printId AS cardId FROM place p " +
            "JOIN album_copy c ON c.printId = p.cardPrintId WHERE c.seenAt IS NOT NULL AND c.status <> 'Spent'",
    )
    suspend fun collectedPlaceCards(): List<CollectedPlaceCard>

    @Query(
        "SELECT c.id AS copyId, m.localDate, COALESCE(m.journeyId, v.journeyId) AS journeyId " +
            "FROM card_memory m JOIN album_copy c ON c.legacyId = m.cardId " +
            "LEFT JOIN visit v ON v.id = m.visitId WHERE c.seenAt IS NOT NULL ORDER BY m.occurredAt DESC, m.id",
    )
    suspend fun importedMemories(): List<ImportedMemory>

    @Query("SELECT * FROM album_wallet WHERE id = 1")
    suspend fun wallet(): WalletEntity?

    @Upsert suspend fun save(wallet: WalletEntity)

    @Upsert suspend fun savePrints(prints: List<PrintEntity>)

    @Query("SELECT * FROM album_print")
    suspend fun prints(): List<PrintEntity>

    @Query("SELECT * FROM album_print WHERE id = :id")
    suspend fun print(id: String): PrintEntity

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun grant(pack: PackEntity): Long

    @Query("SELECT * FROM album_pack WHERE id = :id")
    suspend fun pack(id: String): PackEntity?

    @Query("SELECT * FROM album_pack ORDER BY receivedAt DESC, id")
    suspend fun packs(): List<PackEntity>

    @Insert suspend fun add(copy: CopyEntity)

    @Update suspend fun save(copy: CopyEntity)

    @Query("SELECT * FROM album_copy WHERE packId = :packId")
    suspend fun draw(packId: String): CopyEntity?

    @Query("SELECT * FROM album_copy WHERE id = :id")
    suspend fun copy(id: String): CopyEntity?

    @Query("SELECT * FROM album_copy ORDER BY acquiredAt DESC, id")
    suspend fun copies(): List<CopyEntity>

    @Query("SELECT * FROM album_action ORDER BY at DESC, rowid DESC")
    suspend fun actions(): List<ForgeEntity>

    @Query("SELECT * FROM album_action WHERE id = :id")
    suspend fun action(id: String): ForgeEntity?

    @Query("SELECT * FROM album_action WHERE copyId = :id AND shown = 0 LIMIT 1")
    suspend fun pending(id: String): ForgeEntity?

    @Query("DELETE FROM album_action WHERE outcome = 'LegacyAdvance'")
    suspend fun removeImportedLevelNotices()

    @Insert suspend fun log(action: ForgeEntity)

    @Query("UPDATE album_action SET shown = 1 WHERE id = :id")
    suspend fun acknowledge(id: String)

    @Query("DELETE FROM album_action")
    suspend fun clearActions()

    @Query("DELETE FROM album_copy")
    suspend fun clearCopies()

    @Query("DELETE FROM album_pack")
    suspend fun clearPacks()

    @Query("DELETE FROM album_wallet")
    suspend fun clearWallet()
}

data class CollectedPlaceCard(val placeId: String, val cardId: String)

data class ImportedMemory(val copyId: String, val localDate: String, val journeyId: String?)
