package com.ctify.player.ui.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ctify.player.player.fx.Mood
import com.ctify.player.player.fx.Moods

/** What every big label is called in each mode; anything not listed keeps its normal name. */
object MoodWords {
    private val evil = mapOf(
        "Guava Player" to "Evil Guava", "Home" to "Hell", "Library" to "Crypt", "Radio" to "Séance", "Search" to "Summon",
        "Songs" to "Curses", "Liked" to "Damned", "Liked songs" to "Damned songs", "Playlists" to "Grimoires",
        "Albums" to "Tombs", "Artists" to "Demons", "Downloads" to "Possessed", "Settings" to "Pact",
        "Recently played" to "Recently sacrificed", "Your playlists" to "Your grimoires", "For you" to "For your soul",
        "Most played" to "Most cursed", "Genres" to "Circles of Hell", "Creators" to "Demons", "Up next" to "Next sacrifice",
        "Sound" to "Ritual", "Play" to "Unleash", "Shuffle" to "Chaos", "Now Playing" to "Now Haunting",
        "Around the world" to "Around the underworld", "Continue listening" to "Keep suffering", "Rediscover" to "Resurrect",
        "Live on radio now" to "Live from the abyss", "Heard on the radio" to "Whispers from the abyss",
        "Good morning" to "Welcome to Hell", "Good afternoon" to "Welcome to Hell", "Good evening" to "Welcome to Hell",
        "Favorites" to "Cursed", "Browse" to "Wander the abyss", "Look" to "Mask", "About" to "Origin",
    )
    private val godly = mapOf(
        "Guava Player" to "Holy Guava", "Home" to "Heaven", "Library" to "Scriptures", "Radio" to "Choir", "Search" to "Seek",
        "Songs" to "Hymns", "Liked" to "Blessed", "Liked songs" to "Blessed hymns", "Playlists" to "Psalms",
        "Albums" to "Gospels", "Artists" to "Saints", "Downloads" to "Offerings", "Settings" to "Commandments",
        "Recently played" to "Recently praised", "Your playlists" to "Your psalms", "For you" to "Blessed for you",
        "Most played" to "Most praised", "Genres" to "Choirs", "Creators" to "Prophets", "Up next" to "Next hymn",
        "Sound" to "Grace", "Play" to "Amen", "Shuffle" to "Divine will", "Now Playing" to "Now Praising",
        "Around the world" to "Across creation", "Continue listening" to "Keep the faith", "Rediscover" to "Revelation",
        "Live on radio now" to "Live sermons", "Heard on the radio" to "Heard in the choir",
        "Good morning" to "Peace be with you", "Good afternoon" to "Peace be with you", "Good evening" to "Peace be with you",
        "Favorites" to "Blessed", "Browse" to "Pilgrimage", "Look" to "Halo", "About" to "Genesis",
    )
    private val both = mapOf(
        "Guava Player" to "Good & Evil Guava", "Home" to "Purgatory", "Library" to "Limbo", "Radio" to "Confession",
        "Search" to "Judgement", "Songs" to "Sins & Virtues", "Liked" to "Forgiven", "Liked songs" to "Forgiven songs",
        "Playlists" to "Testaments", "Albums" to "Relics", "Artists" to "Fallen angels", "Downloads" to "Souls",
        "Settings" to "Balance", "Recently played" to "Recently judged", "Your playlists" to "Your testaments",
        "For you" to "For both sides of you", "Most played" to "Most confessed", "Genres" to "Realms",
        "Creators" to "Fallen angels", "Up next" to "Next judgement", "Sound" to "Duality", "Play" to "Decide",
        "Shuffle" to "Fate", "Now Playing" to "Now Judging", "Around the world" to "Between worlds",
        "Continue listening" to "Keep wandering", "Rediscover" to "Rebirth", "Live on radio now" to "Live from limbo",
        "Heard on the radio" to "Heard in confession",
        "Good morning" to "Good & Evil", "Good afternoon" to "Good & Evil", "Good evening" to "Good & Evil",
        "Favorites" to "Forgiven", "Browse" to "Wander", "Look" to "Two faces", "About" to "Prophecy",
    )

    fun of(mood: Mood, text: String): String = when (mood) {
        Mood.NORMAL -> text
        Mood.EVIL -> evil[text] ?: text
        Mood.GODLY -> godly[text] ?: text
        Mood.GOOD_EVIL -> both[text] ?: text
    }
}

/** [text] renamed for the current mode. */
@Composable
fun moodText(text: String): String {
    val mood by Moods.mood.collectAsState()
    return MoodWords.of(mood, text)
}
