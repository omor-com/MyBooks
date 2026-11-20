package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.community.BadgeType
import com.example.data.community.CommunityConstants
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandPurpleDeep
import com.example.ui.theme.BrandPurplePrimary
import java.io.File
import java.util.Locale

@Composable
fun BookCoverThumbnail(
    title: String,
    formatLabel: String,
    coverPath: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.aspectRatio(0.70f),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val coverFile = coverPath?.takeIf { it.isNotBlank() }?.let { File(it) }
            if (coverFile != null && coverFile.exists()) {
                AsyncImage(
                    model = coverFile,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(BrandPurpleDeep, BrandPurplePrimary)
                            )
                        )
                        .padding(10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = BrandGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Text(
                    text = formatLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun VerifiedBadgeIcon(
    badgeType: BadgeType,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    when (badgeType) {
        BadgeType.RED_ADMIN -> {
            Image(
                painter = painterResource(id = R.drawable.ic_verified_badge_admin_red),
                contentDescription = stringResource(R.string.community_badge_admin),
                modifier = modifier.size(size)
            )
        }
        BadgeType.BLUE -> {
            Image(
                painter = painterResource(id = R.drawable.ic_verified_badge_blue),
                contentDescription = stringResource(R.string.community_badge_verified),
                modifier = modifier.size(size)
            )
        }
        BadgeType.NONE -> Unit
    }
}

@Composable
fun CommunityAvatar(
    photoUrl: String?,
    displayName: String,
    isAdmin: Boolean = false,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val resolvedUrl = photoUrl?.takeIf { it.isNotBlank() }
        ?: if (isAdmin) CommunityConstants.OFFICIAL_ADMIN_AVATAR_URL else CommunityConstants.DEFAULT_USER_AVATAR_URL

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size * 0.55f)
        )
        AsyncImage(
            model = resolvedUrl,
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024.0 && idx < units.lastIndex) {
        value /= 1024.0
        idx++
    }
    return if (idx == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[idx])
}
