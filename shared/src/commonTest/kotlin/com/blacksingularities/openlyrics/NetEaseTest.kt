package com.blacksingularities.openlyrics

import com.blacksingularities.openlyrics.lyrics.NetEase
import kotlin.test.Test
import kotlin.test.assertEquals

class NetEaseTest {
    @Test
    fun dropsLeadingCredits() {
        val raw = """
            [00:00.000] 作曲 : Jan Kowalski
            [00:01.000] 作词 : Anna Nowak
            [00:02.000] Lyrics by: Anna Nowak
            [00:12.500]Pierwszy wiersz o świcie
            [00:16.000]Drugi wiersz: o zmierzchu
        """.trimIndent()
        assertEquals("[00:12.500]Pierwszy wiersz o świcie\n[00:16.000]Drugi wiersz: o zmierzchu", NetEase.clean(raw))
    }

    @Test
    fun keepsLyricsWithoutCredits() {
        val raw = "[00:05.000]Tylko tekst\n[00:09.000]Bez dopisków"
        assertEquals(raw, NetEase.clean(raw))
    }
}
