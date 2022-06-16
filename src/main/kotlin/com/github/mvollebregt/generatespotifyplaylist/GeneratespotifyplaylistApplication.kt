package com.github.mvollebregt.generatespotifyplaylist

import com.neovisionaries.i18n.CountryCode
import org.jsoup.Jsoup
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import se.michaelthelin.spotify.SpotifyApi
import java.awt.Desktop

@SpringBootApplication
class GeneratespotifyplaylistApplication : CommandLineRunner {

    val sourceUrl =
        "https://www.vera-groningen.nl/wp/wp-admin/admin-ajax.php?action=renderProgramme&category=concert&page=1&perpage=80"

    val cssQuery = "h3.artist"

    val spotifyApi: SpotifyApi = SpotifyApi.Builder()
        .setClientId(clientId)
        .setClientSecret(clientSecret)
        .setRefreshToken(refreshToken)
        .setRedirectUri(redirectUri)
        .build()

    override fun run(vararg args: String?) {

//        runOnce1(); // run once to obtain an authorization code
//        runOnce2(); // run once to get the refresh token for the authorization code

        authorize()

        val uris = ArrayList<String>()
        getArtists().forEach { artistName ->
            val topTrack = findTopTrackForArtist(artistName)
            topTrack?.also { uris.add(it) }
        }
        spotifyApi.replacePlaylistsItems(playlistId, uris.toTypedArray()).build().execute()
    }

    private fun runOnce1() {
        val authorizationCodeUri = spotifyApi.authorizationCodeUri()
            .scope("playlist-modify-public")
            .build().execute()
        Desktop.getDesktop().browse(authorizationCodeUri)
    }

    private fun runOnce2() {
        val retrievedCode = "" // Fill in the code retrieved from "runOnce1()
        val credentials = spotifyApi.authorizationCode(retrievedCode).build().execute()
        println(credentials.refreshToken)

    }

    private fun authorize() {
        val credentials = spotifyApi.authorizationCodeRefresh().build().execute()
        spotifyApi.accessToken = credentials.accessToken
    }

    private fun getArtists(): List<String> {
        println("Fetching concerts\n")
        return Jsoup.connect(sourceUrl).get()
            .select(cssQuery)
            .flatMap { it.textNodes() }
            .map { it.text() }
            .map { it.trimStart(' ', '+') }
            .distinct()
    }

    private fun findTopTrackForArtist(artistName: String): String? {
        val foundArtists = spotifyApi.searchArtists(artistName).build().execute().items
        if (foundArtists.isNotEmpty()) {
            val artist = foundArtists[0]
            if (adjustForComparing(artistName).equals(adjustForComparing(artist.name))) {
                val topTracks = spotifyApi.getArtistsTopTracks(artist.id, CountryCode.NL).build().execute()
                if (topTracks.isNotEmpty()) {
                    println("${artist.name} - ${topTracks[0].name}")
                    return topTracks[0].uri
                }
            } else {
                println("Could not find $artistName")
            }
        }
        return null
    }

    private fun adjustForComparing(artistName: String) =
        artistName.trim().lowercase().split("\\W+".toRegex()).joinToString(" ")
}

fun main(args: Array<String>) {
    runApplication<GeneratespotifyplaylistApplication>(*args)
}
