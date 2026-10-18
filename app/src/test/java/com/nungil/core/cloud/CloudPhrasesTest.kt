package com.nungil.core.cloud

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudPhrasesTest {
    private val phrases: List<(Lang) -> String> = listOf(
        CloudPhrases::looking, CloudPhrases::offline, CloudPhrases::notSetUp, CloudPhrases::failed, CloudPhrases::question,
    )

    @Test fun everyPhraseExistsInBothLanguages() {
        for (phrase in phrases) {
            val en = phrase(Lang.EN)
            val ko = phrase(Lang.KO)
            assertTrue(en, en.isNotBlank())
            assertTrue(ko, ko.isNotBlank())
            assertNotEquals(en, ko)
        }
    }

    @Test fun theWordsAreTheSpecsWords() {
        assertEquals("Looking closer.", CloudPhrases.looking(Lang.EN))
        assertEquals("No internet. Nungil keeps working offline.", CloudPhrases.offline(Lang.EN))
        assertEquals("Cloud look is not set up.", CloudPhrases.notSetUp(Lang.EN))
        assertEquals("The cloud did not answer.", CloudPhrases.failed(Lang.EN))
        assertEquals("Describe what is in front of me.", CloudPhrases.question(Lang.EN))
        assertEquals("자세히 볼게요.", CloudPhrases.looking(Lang.KO))
    }
}
