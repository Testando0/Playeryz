/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.yearinmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.utils.makeTimeString

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RankedArtistsRecapCard(
    card: YearInMusicRecapCard.RankedArtists,
    applySafeContentInsets: Boolean,
    onArtistLongClick: (Artist) -> Unit,
) {
    RecapCardContent(
        badge = stringResource(R.string.top_artists),
        footer = stringResource(R.string.year_in_music_ranked_artists),
        verticalArrangement = Arrangement.SpaceBetween,
        applySafeContentInsets = applySafeContentInsets,
    ) {
        val topArtist = card.artists.firstOrNull()
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.year_in_music_ranked_artists),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = RecapCream,
                lineHeight = 42.sp,
            )
            topArtist?.let { artist ->
                ArtistHero(artist = artist)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            card.artists.take(4).forEachIndexed { index, artist ->
                RankedArtistRow(
                    rank = index + 1,
                    artist = artist,
                    modifier =
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { onArtistLongClick(artist) },
                        ),
                )
            }
        }
    }
}
@Composable
internal fun RankedAlbumsRecapCard(
    card: YearInMusicRecapCard.RankedAlbums,
    applySafeContentInsets: Boolean,
) {
    RecapCardContent(
        badge = stringResource(R.string.albums),
        footer = stringResource(R.string.year_in_music_ranked_albums),
        verticalArrangement = Arrangement.SpaceBetween,
        applySafeContentInsets = applySafeContentInsets,
    ) {
        val topAlbum = card.albums.firstOrNull()
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.year_in_music_ranked_albums),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = RecapCream,
                lineHeight = 42.sp,
            )
            topAlbum?.let { album ->
                AlbumHero(album = album)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            card.albums.take(4).forEachIndexed { index, album ->
                RankedAlbumRow(
                    rank = index + 1,
                    album = album,
                )
            }
        }
    }
}

@Composable
private fun ArtistHero(artist: Artist) {
    val imageModel = rememberShareSafeImageRequest(artist.artist.thumbnailUrl)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(RecapTokens.SectionRadius))
                .border(1.dp, RecapRed.copy(alpha = 0.52f), RoundedCornerShape(RecapTokens.SectionRadius)),
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(128.dp),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(RecapBlack.copy(alpha = 0.9f), RecapBlack.copy(alpha = 0.12f)),
                        ),
                    ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("#1", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = RecapRed)
            Text(
                text = artist.artist.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = RecapCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(makeTimeString(artist.timeListened?.toLong()), style = MaterialTheme.typography.labelMedium, color = RecapCream.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun AlbumHero(album: Album) {
    val imageModel = rememberShareSafeImageRequest(album.thumbnailUrl)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(RecapTokens.SectionRadius))
                .border(1.dp, RecapYellow.copy(alpha = 0.52f), RoundedCornerShape(RecapTokens.SectionRadius)),
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(128.dp),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(RecapBlack.copy(alpha = 0.9f), RecapBlack.copy(alpha = 0.12f)),
                        ),
                    ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("#1", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = RecapYellow)
            Text(
                text = album.album.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = RecapCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(makeTimeString(album.timeListened), style = MaterialTheme.typography.labelMedium, color = RecapCream.copy(alpha = 0.72f))
        }
    }
}
