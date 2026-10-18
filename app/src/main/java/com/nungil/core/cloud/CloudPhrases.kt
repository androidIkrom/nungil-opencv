package com.nungil.core.cloud

import com.nungil.contract.Lang

/** What "look closer" says around the cloud's answer, in English and Korean. */
object CloudPhrases {
    fun looking(lang: Lang): String = if (lang == Lang.KO) "자세히 볼게요." else "Looking closer."

    fun offline(lang: Lang): String =
        if (lang == Lang.KO) "인터넷이 없어요. 눈길은 오프라인으로 계속 작동해요." else "No internet. Nungil keeps working offline."

    fun notSetUp(lang: Lang): String = if (lang == Lang.KO) "클라우드 보기가 설정되지 않았어요." else "Cloud look is not set up."

    fun failed(lang: Lang): String = if (lang == Lang.KO) "클라우드가 응답하지 않았어요." else "The cloud did not answer."

    /** The question sent with the frame. */
    fun question(lang: Lang): String = if (lang == Lang.KO) "앞에 무엇이 있는지 설명해 주세요." else "Describe what is in front of me."
}
