package io.issac.pleosandroid.core.data.cards

import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup

internal val regionalPrints = buildList {
    fun edition(
        packType: PackType,
        group: PrintGroup,
        key: String,
        title: String,
        place: String,
        number: Int,
        mapRegionId: String? = null,
    ) {
        val art = "print_" + key.replace('-', '_')
        add(
            CardPrint(
                key, title, packType, group, group.grade,
                "${art}_base", if (packType == PackType.Hyundai) null else "${art}_variant", place,
                packType.name.uppercase(java.util.Locale.ROOT) + " / " + number.toString().padStart(3, '0'),
                when (packType) {
                    PackType.Seoul -> "KR-11"
                    PackType.Busan -> "KR-26"
                    PackType.Jeju -> "KR-49"
                    PackType.Hyundai -> requireNotNull(mapRegionId)
                },
            ),
        )
    }

    edition(PackType.Seoul, PrintGroup.Landmark, "seoul-gyeongbokgung", "경복궁의 아침", "경복궁", 1)
    edition(PackType.Seoul, PrintGroup.Landmark, "seoul-lotte-tower", "롯데타워", "롯데타워", 2)
    edition(PackType.Seoul, PrintGroup.Landmark, "seoul-changgyeonggung", "창경궁", "창경궁", 3)
    edition(PackType.Seoul, PrintGroup.Landmark, "seoul-naksan", "낙산", "낙산", 4)
    edition(PackType.Seoul, PrintGroup.Landmark, "seoul-bukhansan", "북한산", "북한산", 5)
    edition(PackType.Seoul, PrintGroup.Landmark, "seoul-blue-house", "청와대", "청와대", 6)
    edition(PackType.Seoul, PrintGroup.Landmark, "seoul-n-tower", "서울N타워", "서울N타워", 7)
    edition(PackType.Seoul, PrintGroup.Landmark, "seoul-the-hyundai", "더현대", "더현대", 8)
    edition(PackType.Seoul, PrintGroup.Specialty, "seoul-haechi", "해치", "해치", 9)
    edition(PackType.Seoul, PrintGroup.Specialty, "seoul-cheongryong", "소울프렌즈 청룡", "소울프렌즈 청룡", 10)
    edition(PackType.Seoul, PrintGroup.Specialty, "seoul-baekho", "소울프렌즈 백호", "소울프렌즈 백호", 11)
    edition(PackType.Seoul, PrintGroup.Specialty, "seoul-jujak", "소울프렌즈 주작", "소울프렌즈 주작", 12)
    edition(PackType.Seoul, PrintGroup.Specialty, "seoul-hyeonmu", "소울프렌즈 현무", "소울프렌즈 현무", 13)
    edition(PackType.Busan, PrintGroup.Beach, "busan-haeundae", "해운대의 파도", "해운대해수욕장", 1)
    edition(PackType.Busan, PrintGroup.Beach, "busan-gwangalli", "광안리의 물결", "광안리해수욕장", 2)
    edition(PackType.Busan, PrintGroup.Beach, "busan-dadaepo", "다대포해수욕장", "다대포해수욕장", 3)
    edition(PackType.Busan, PrintGroup.Landmark, "busan-gamcheon", "감천문화마을", "감천문화마을", 4)
    edition(PackType.Busan, PrintGroup.Landmark, "busan-beomeosa", "범어사", "범어사", 5)
    edition(PackType.Busan, PrintGroup.Landmark, "busan-kkangtong-market", "깡통시장", "깡통시장", 6)
    edition(PackType.Busan, PrintGroup.Landmark, "busan-jagalchi-market", "자갈치시장", "자갈치시장", 7)
    edition(PackType.Busan, PrintGroup.Landmark, "busan-millak-waterfront", "수변공원", "수변공원", 8)
    edition(PackType.Busan, PrintGroup.Specialty, "busan-bukangi", "북항의 부캉이", "부캉이", 9)
    edition(PackType.Busan, PrintGroup.Specialty, "busan-gukbap", "국밥", "국밥", 10)
    edition(PackType.Busan, PrintGroup.Specialty, "busan-ssiat-hotteok", "씨앗호떡", "씨앗호떡", 11)
    edition(PackType.Jeju, PrintGroup.Beach, "jeju-hyeopjae", "협재해수욕장", "협재해수욕장", 1)
    edition(PackType.Jeju, PrintGroup.Beach, "jeju-hamdeok", "함덕해수욕장", "함덕해수욕장", 2)
    edition(PackType.Jeju, PrintGroup.Beach, "jeju-woljeongri", "월정리해수욕장", "월정리해수욕장", 3)
    edition(PackType.Jeju, PrintGroup.Landmark, "jeju-seongsan", "성산일출봉", "성산일출봉", 4)
    edition(PackType.Jeju, PrintGroup.Landmark, "jeju-hallasan", "한라산", "한라산", 5)
    edition(PackType.Jeju, PrintGroup.Landmark, "jeju-aewol", "애월", "애월", 6)
    edition(PackType.Jeju, PrintGroup.Landmark, "jeju-olle-market", "서귀포매일올레시장", "서귀포매일올레시장", 7)
    edition(PackType.Jeju, PrintGroup.Landmark, "jeju-dongmun-market", "동문시장", "동문시장", 8)
    edition(PackType.Jeju, PrintGroup.Specialty, "jeju-dolhareubang", "제주의 돌하르방", "돌하르방", 9)
    edition(PackType.Jeju, PrintGroup.Specialty, "jeju-tangerine", "귤", "귤", 10)
    edition(PackType.Jeju, PrintGroup.Specialty, "jeju-black-pig", "흑돼지", "흑돼지", 11)
    edition(PackType.Hyundai, PrintGroup.Studio, "hyundai-motorstudio-goyang", "현대 모터스튜디오 고양", "경기 고양", 1, "KR-41")
    edition(PackType.Hyundai, PrintGroup.Studio, "hyundai-motorstudio-busan", "현대 모터스튜디오 부산", "부산", 2, "KR-26")
    edition(PackType.Hyundai, PrintGroup.Studio, "hyundai-motorstudio-seoul", "현대 모터스튜디오 서울", "서울", 3, "KR-11")
    edition(PackType.Hyundai, PrintGroup.Studio, "hyundai-motorstudio-hanam", "현대 모터스튜디오 하남", "경기 하남", 4, "KR-41")
    edition(PackType.Hyundai, PrintGroup.Studio, "hyundai-seocho-ux", "현대 서초 UX 스튜디오", "서울 서초", 5, "KR-11")
    edition(PackType.Hyundai, PrintGroup.Epit, "epit-busan-dongbaek", "부산 동백공원 E-pit", "부산 해운대", 6, "KR-26")
    edition(PackType.Hyundai, PrintGroup.Epit, "epit-seoul-station", "서울역 E-pit", "서울역", 7, "KR-11")
    edition(PackType.Hyundai, PrintGroup.Epit, "epit-gwangju-stadium", "광주 월드컵경기장 E-pit", "광주", 8, "KR-29")
    edition(PackType.Hyundai, PrintGroup.Epit, "epit-haevichi", "해비치호텔앤드리조트 E-pit", "제주 서귀포", 9, "KR-49")
    edition(PackType.Hyundai, PrintGroup.Epit, "epit-daejeon-science", "대전 국립중앙과학관 E-pit", "대전", 10, "KR-30")
}
