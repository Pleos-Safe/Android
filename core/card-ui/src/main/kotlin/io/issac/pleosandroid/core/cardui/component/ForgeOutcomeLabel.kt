package io.issac.pleosandroid.core.cardui.component

import io.issac.pleosandroid.core.domain.cards.ForgeOutcome

val ForgeOutcome.label: String get() = when (this) {
    ForgeOutcome.Success -> "강화 성공"
    ForgeOutcome.GreatSuccess -> "대성공"
    ForgeOutcome.Hold -> "강화 유지"
    ForgeOutcome.Drop -> "강화 하락"
    ForgeOutcome.Destroyed -> "카드 파괴"
    ForgeOutcome.Restored -> "복구 완료"
}
