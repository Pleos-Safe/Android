package io.issac.pleosandroid.core.data.collection.mapper

import io.issac.pleosandroid.core.data.driving.decodeDriving
import io.issac.pleosandroid.core.data.driving.encode
import io.issac.pleosandroid.core.database.entity.CardDefinitionEntity
import io.issac.pleosandroid.core.database.entity.CardLevelRuleEntity
import io.issac.pleosandroid.core.database.entity.CardMemoryEntity
import io.issac.pleosandroid.core.database.entity.JourneyEntity
import io.issac.pleosandroid.core.database.entity.PlaceEntity
import io.issac.pleosandroid.core.database.entity.VisitEntity
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.CardDefinition
import io.issac.pleosandroid.core.domain.collection.model.CardFront
import io.issac.pleosandroid.core.domain.collection.model.CardKind
import io.issac.pleosandroid.core.domain.collection.model.CardLevelRule
import io.issac.pleosandroid.core.domain.collection.model.CardMemory
import io.issac.pleosandroid.core.domain.collection.model.CardTemplate
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.Place
import java.time.Instant
import java.time.LocalDate

internal fun PlaceEntity.toDomain() = Place(
    id, name, district, category, latitude, longitude, radiusMeters, minimumDwellSeconds, imageKey, active, regionId,
)
internal fun Place.toEntity() = PlaceEntity(
    id, name, district, category, latitude, longitude, radiusMeters, minimumDwellSeconds, imageKey, active, regionId,
)
internal fun CardDefinitionEntity.toDomain() = CardDefinition(
    id,
    CardKind.valueOf(kind),
    placeId,
    CardFront(title, subtitle, context, series, artworkKey, glossKey, engravingKey, CardTemplate.valueOf(template)),
    ruleVersion,
    active,
)
internal fun CardDefinition.toEntity() = CardDefinitionEntity(
    id, kind.name, placeId, front.title, front.subtitle, front.context, front.series,
    front.artworkKey, front.glossKey, front.engravingKey, front.template.name, ruleVersion, active,
)
internal fun CardLevelRuleEntity.toDomain() = CardLevelRule(
    cardId,
    level,
    version,
    requiredCount,
    Instant.ofEpochMilli(eligibleFrom),
    minimumScore,
    minimumDistanceMeters,
    maximumHarshStarts,
)
internal fun CardLevelRule.toEntity() = CardLevelRuleEntity(
    cardId,
    level,
    version,
    requiredCount,
    eligibleFrom.toEpochMilli(),
    minimumScore,
    minimumDistanceMeters,
    maximumHarshStarts,
)
internal fun VisitEntity.toDomain() = ConfirmedVisit(
    id, placeId, journeyId, Instant.ofEpochMilli(arrivedAt), Instant.ofEpochMilli(departedAt),
    Instant.ofEpochMilli(confirmedAt), zoneId, distanceFromPlaceMeters, accuracyMeters, qualificationVersion,
)
internal fun ConfirmedVisit.toEntity() = VisitEntity(
    id, placeId, journeyId, arrivedAt.toEpochMilli(), departedAt.toEpochMilli(), confirmedAt.toEpochMilli(),
    zoneId, distanceFromPlaceMeters, accuracyMeters, qualificationVersion,
)
internal fun JourneyEntity.toDomain() = JourneyRecord(
    id, Instant.ofEpochMilli(startedAt), Instant.ofEpochMilli(endedAt), zoneId, distanceMeters,
    AssessmentStatus.valueOf(assessment), score, harshStarts, assessmentVersion, decodeDriving(drivingJson),
)
internal fun JourneyRecord.toEntity() = JourneyEntity(
    id, startedAt.toEpochMilli(), endedAt.toEpochMilli(), zoneId, distanceMeters,
    assessment.name, score, harshStarts, assessmentVersion, driving?.encode(),
)
internal fun CardMemoryEntity.toDomain() = CardMemory(
    id,
    Instant.ofEpochMilli(occurredAt),
    LocalDate.parse(localDate),
    visitId,
    journeyId,
)
