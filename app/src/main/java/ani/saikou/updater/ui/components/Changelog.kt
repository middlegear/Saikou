package ani.saikou.updater.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun ChangelogSection(
    version: String,
    changelog: String,
    modifier: Modifier = Modifier
) {
    val cleanedChangelog = remember(changelog) {
        changelog
            .replace(Regex("^#+\\s*Version.*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    val scrollState = rememberScrollState()


    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val targetHeight = (screenHeightDp * 0.35f).coerceAtLeast(240.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Version $version",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(targetHeight)
                .verticalScroll(scrollState)
        ) {
            Markdown(
                content = cleanedChangelog.ifEmpty { "No release notes provided." },
                colors = markdownColor(
                    text = MaterialTheme.colorScheme.onSurface,
                    codeText = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                typography = markdownTypography(
                    h1 = MaterialTheme.typography.titleLarge,
                    h2 = MaterialTheme.typography.titleMedium,
                    h3 = MaterialTheme.typography.titleSmall,
                    paragraph = MaterialTheme.typography.bodyMedium,
                    text = MaterialTheme.typography.bodyMedium,
                    list = MaterialTheme.typography.bodyMedium
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}