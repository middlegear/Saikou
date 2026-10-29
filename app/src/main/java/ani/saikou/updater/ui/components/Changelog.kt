package ani.saikou.updater.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ChangelogSection(
    version: String,
    changelog: String,
    modifier: Modifier = Modifier
) {
    val cleanedChangelog = remember(changelog) {
        changelog
            .replace(Regex("^#+\\s*Version.*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)), "")
            .trim()
    }

    val scrollState = rememberScrollState()

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
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (cleanedChangelog.isBlank()) {
                    Text(
                        text = "No release notes provided.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val sections = remember(cleanedChangelog) { parseGitHubChangelog(cleanedChangelog) }

                    sections.forEach { section ->
                        ChangelogSectionBlock(section = section)
                    }
                }
            }
        }
    }
}

data class ChangelogSectionData(
    val categoryTitle: String?,
    val items: List<String>
)

private fun parseGitHubChangelog(rawText: String): List<ChangelogSectionData> {
    val normalizedText = rawText.replace(Regex("\r\n?") , "\n")
    val lines = normalizedText.lines().map { it.trim() }.filter { it.isNotBlank() }

    val sections = mutableListOf<ChangelogSectionData>()
    var currentCategory: String? = null
    var currentItems = mutableListOf<String>()

    for (line in lines) {
        when {

            line.startsWith("##") -> {
                if (currentItems.isNotEmpty() || currentCategory != null) {
                    sections.add(ChangelogSectionData(currentCategory, currentItems))
                    currentItems = mutableListOf()
                }
                currentCategory = line.trimStart('#').trim()
            }

            line.startsWith("- ") || line.startsWith("* ") -> {
                var bulletContent = line.removePrefix("- ").removePrefix("* ").trim()

                bulletContent = bulletContent
                    .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                    .replace(Regex("\\*(.*?)\\*"), "$1")
                currentItems.add(bulletContent)
            }
            else -> {
                if (currentItems.isNotEmpty()) {
                    val lastIndex = currentItems.size - 1

                    val cleanedLine = line
                        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                        .replace(Regex("\\*(.*?)\\*"), "$1")
                    currentItems[lastIndex] = "${currentItems[lastIndex]} $cleanedLine"
                } else {
                    currentItems.add(line)
                }
            }
        }
    }

    if (currentItems.isNotEmpty() || currentCategory != null) {
        sections.add(ChangelogSectionData(currentCategory, currentItems))
    }

    if (sections.isEmpty()) {
        sections.add(ChangelogSectionData(null, listOf(rawText)))
    }

    return sections
}

@Composable
private fun ChangelogSectionBlock(section: ChangelogSectionData) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!section.categoryTitle.isNullOrBlank()) {
            Text(
                text = section.categoryTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        section.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}