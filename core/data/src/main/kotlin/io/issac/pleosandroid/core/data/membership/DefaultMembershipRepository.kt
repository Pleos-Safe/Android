package io.issac.pleosandroid.core.data.membership

import androidx.room.withTransaction
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.storageResult
import io.issac.pleosandroid.core.data.driving.DrivingClock
import io.issac.pleosandroid.core.database.entity.MembershipTermEntity
import io.issac.pleosandroid.core.domain.membership.Membership
import io.issac.pleosandroid.core.domain.membership.MembershipAction
import io.issac.pleosandroid.core.domain.membership.MembershipChange
import io.issac.pleosandroid.core.domain.membership.MembershipRepository
import io.issac.pleosandroid.core.domain.membership.MembershipStatus
import io.issac.pleosandroid.core.domain.membership.MembershipTerm
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

internal fun MembershipTermEntity.toDomain() = MembershipTerm(
    Instant.ofEpochMilli(startedAt),
    endsAt?.let(Instant::ofEpochMilli),
)

internal class DefaultMembershipRepository @Inject constructor(
    private val store: CollectionStore,
    private val clock: DrivingClock,
) : MembershipRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMembership() = store.observeProfile { db, demo ->
        val dao = db.collectionDao()
        dao.observeRevision().map {
            db.withTransaction { checkNotNull(dao.userState()).generation to dao.latestMembership() }
        }.distinctUntilChanged().transformLatest { (generation, row) ->
            val term = row?.toDomain()
            while (true) {
                val now = clock.now()
                val state = Membership.at(generation, demo, term, now, row?.revision ?: 0)
                emit(state)
                val nextChange = when {
                    state.hasBenefit -> state.periodEnd
                    term != null && now < term.startedAt -> term.startedAt
                    else -> null
                } ?: break
                delay(Duration.between(clock.now(), nextChange).toMillis().coerceAtLeast(1L))
            }
        }
    }

    override suspend fun change(action: MembershipAction, expected: Membership): MembershipChange =
        storageResult(MembershipChange.Unavailable) {
            store.transaction {
                val now = clock.now()
                val row = latestMembership()
                val actual = Membership.at(
                    checkNotNull(userState()).generation,
                    store.demoMode.value,
                    row?.toDomain(),
                    now,
                    row?.revision ?: 0,
                )
                if (actual != expected) return@transaction MembershipChange.Changed

                when (action) {
                    MembershipAction.Subscribe -> {
                        if (actual.hasBenefit || (row != null && now.toEpochMilli() < row.startedAt)) {
                            return@transaction MembershipChange.Changed
                        }
                        insertMembership(
                            MembershipTermEntity(
                                startedAt = now.toEpochMilli(),
                                endsAt = null,
                                revision =
                                    actual.revision + 1,
                            ),
                        )
                    }

                    MembershipAction.CancelRenewal -> {
                        if (actual.status != MembershipStatus.Renewing) return@transaction MembershipChange.Changed
                        setMembershipEnd(checkNotNull(row).id, checkNotNull(actual.periodEnd).toEpochMilli())
                    }

                    MembershipAction.ResumeRenewal -> {
                        if (actual.status != MembershipStatus.Ending) return@transaction MembershipChange.Changed
                        setMembershipEnd(checkNotNull(row).id, null)
                    }
                }
                touch()
                MembershipChange.Applied
            }
        }
}
