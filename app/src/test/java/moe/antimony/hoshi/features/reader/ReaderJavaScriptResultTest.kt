package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderJavaScriptResultTest {
    @Test
    fun decodesEscapeSequences() {
        assertEquals("一行\n二行", readerJavaScriptStringResult("\"一行\\n二行\""))
        assertEquals("a\tb", readerJavaScriptStringResult("\"a\\tb\""))
    }

    @Test
    fun decodesQuotesAndSurrogatePairs() {
        assertEquals("说\"好\"", readerJavaScriptStringResult("\"说\\\"好\\\"\""))
        assertEquals("𠮷", readerJavaScriptStringResult("\"𠮷\""))
    }

    @Test
    fun treatsNullishAndBlankResultsAsEmpty() {
        assertEquals("", readerJavaScriptStringResult(null))
        assertEquals("", readerJavaScriptStringResult(""))
        assertEquals("", readerJavaScriptStringResult("   "))
        assertEquals("", readerJavaScriptStringResult("null"))
        assertEquals("", readerJavaScriptStringResult("undefined"))
    }

    @Test
    fun treatsNonStringResultsAsEmpty() {
        assertEquals("", readerJavaScriptStringResult("{}"))
        assertEquals("", readerJavaScriptStringResult("42"))
    }
}
