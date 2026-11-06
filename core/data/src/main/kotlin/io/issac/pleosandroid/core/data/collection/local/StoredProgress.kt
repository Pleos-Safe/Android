package io.issac.pleosandroid.core.data.collection.local

import io.issac.pleosandroid.core.domain.collection.model.CardDefinition
import io.issac.pleosandroid.core.domain.collection.model.CardProgress
import io.issac.pleosandroid.core.domain.collection.policy.AchievementEvaluator

/** Preview and writes use the same policy, including grants earned under an older rule version. */
internal fun StoredCollection.evaluate(
    card: CardDefinition,
    allowReduction: Boolean,
    allowGrowth: Boolean = true,
): CardProgress {
    val evaluator = AchievementEvaluator()
    val old = owned.find { it.cardId == card.id }
    val evaluated = evaluator.evaluate(card, rules, visits, journeys, old?.revealedLevel)
    val retained = if (allowReduction) {
        levels.filter { it.ownershipId == old?.ownershipId }.filter { grant ->
            evaluator.evaluate(card.copy(ruleVersion = grant.ruleVersion), rules, visits, journeys)
                .earnedLevel >= grant.level
        }.maxOfOrNull { it.level } ?: 0
    } else {
        old?.earnedLevel ?: 0
    }
    val eligible = maxOf(evaluated.earnedLevel.takeIf { card.active } ?: 0, retained)
    val earned = if (allowGrowth) eligible else minOf(eligible, old?.earnedLevel ?: 0)
    return evaluated.copy(
        earnedLevel = earned,
        revealedLevel = old?.revealedLevel?.coerceAtMost(earned)?.takeIf {
            it >
                0
        },
    )
}
