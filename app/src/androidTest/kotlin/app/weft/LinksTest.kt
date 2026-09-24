package app.weft

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.weft.privacy.Links
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Tracking parameters come off; everything else stays exactly as typed. */
@RunWith(AndroidJUnit4::class)
class LinksTest {
    @Test fun utmGone() = assertEquals(
        "https://example.org/a?id=7",
        Links.cleanUrl("https://example.org/a?utm_source=x&id=7&utm_medium=y"),
    )
    @Test fun onlyTrackersLeavesNoQuestionMark() = assertEquals(
        "https://example.org/a",
        Links.cleanUrl("https://example.org/a?fbclid=abc&gclid=def"),
    )
    @Test fun fragmentKept() = assertEquals(
        "https://example.org/a?q=1#part",
        Links.cleanUrl("https://example.org/a?q=1&igshid=zz#part"),
    )
    @Test fun siOnlyWhereItTracks() {
        assertEquals("https://youtu.be/abc?t=30", Links.cleanUrl("https://youtu.be/abc?si=XyZ&t=30"))
        assertEquals("https://example.org/?si=keep", Links.cleanUrl("https://example.org/?si=keep"))
    }
    @Test fun noQueryUntouched() = assertEquals("https://example.org/a", Links.cleanUrl("https://example.org/a"))
    @Test fun textAroundKept() = assertEquals(
        "look: https://news.example/x?id=2. and http://b.example/?a=1!",
        Links.clean("look: https://news.example/x?id=2&utm_campaign=z. and http://b.example/?a=1&fbclid=q!"),
    )
    @Test fun sentencePunctuationNotPartOfLink() =
        assertEquals(listOf("https://a.example/x"), Links.find("see https://a.example/x).").map { "see https://a.example/x).".substring(it) })
    @Test fun plainTextUntouched() = assertEquals("no links here", Links.clean("no links here"))
}
