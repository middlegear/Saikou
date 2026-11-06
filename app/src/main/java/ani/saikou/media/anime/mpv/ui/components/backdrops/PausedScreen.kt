package ani.saikou.media.anime.mpv.ui.components.backdrops

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PausedScreen(
    isVisible: Boolean,
    logoUrl: String?,
    title: String?,
    description: String?
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0A0B0E).copy(alpha = 0.98f),
                            Color(0xFF0A0B0E).copy(alpha = 0.90f),
                            Color(0xFF0A0B0E).copy(alpha = 0.50f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = Float.POSITIVE_INFINITY
                    )
                ),
            contentAlignment = Alignment.CenterStart
        ) {

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.6f)
                    .padding(start = 64.dp, end = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                // 0. "YOU'RE WATCHING" Label
                Text(
                    text = "YOU'RE WATCHING",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 1. Logo
                if (!logoUrl.isNullOrEmpty()) {
                    GlideImage(
                        model = logoUrl,
                        contentDescription = "Media Logo",
                        modifier = Modifier
                            .height(90.dp)
                            .fillMaxWidth(0.75f),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 2. Title
                if (!title.isNullOrEmpty()) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 3. Description
                if (!description.isNullOrEmpty()) {
                    Text(
                        text = description,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 20.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}