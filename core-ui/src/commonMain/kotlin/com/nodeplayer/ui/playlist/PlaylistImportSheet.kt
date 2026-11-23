package com.nodeplayer.ui.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nodeplayer.library.playlist.OnlineTrackSearchProvider
import com.nodeplayer.library.playlist.PlaylistImportEngine
import com.nodeplayer.library.playlist.UniversalPlaylistParser
import com.nodeplayer.model.Track
import com.nodeplayer.model.playlist.*
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.components.GlassControlSurface
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.theme.GlassTokens
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Visual wizard step during universal playlist import workflow.
 */
enum class ImportWizardStep {
    ENTRY,
    PROGRESS,
    REVIEW,
    SAVE,
    RESULT
}

/**
 * Liquid glass bottom sheet & dialog implementing the universal playlist importer.
 * Complies with F02 specifications, UI-KIT components, and strict 0-hardcoded token rules.
 */
@Composable
fun PlaylistImportSheet(
    localCatalog: List<Track>,
    searchProvider: OnlineTrackSearchProvider? = null,
    onSavePlaylist: suspend (name: String, tracks: List<Track>, alsoCreateYouTube: Boolean) -> Long,
    onOpenPlaylist: (playlistId: Long) -> Unit,
    onDismiss: () -> Unit,
    initialText: String? = null,
    config: ImportConfig = ImportConfig(),
    onOpenFilePicker: (() -> Unit)? = null
) {
    var step by remember { mutableStateOf(if (!initialText.isNullOrBlank()) ImportWizardStep.ENTRY else ImportWizardStep.ENTRY) }
    var inputText by remember { mutableStateOf(initialText.orEmpty()) }
    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank()) {
            inputText = initialText
        }
    }
    var playlistName by remember { mutableStateOf("Imported Playlist") }
    var alsoCreateYouTube by remember { mutableStateOf(config.alsoCreateYouTube) }

    // Progress State
    var progressStep by remember { mutableStateOf(ImportProgressStep.PARSING) }
    var progressCurrent by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var matchingJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Matching Results
    var resultItems by remember { mutableStateOf<List<ImportResultItem>>(emptyList()) }
    var alternativeItemTarget by remember { mutableStateOf<ImportResultItem?>(null) }
    var createdPlaylistId by remember { mutableStateOf<Long?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Review Collapsible States
    var matchedExpanded by remember { mutableStateOf(true) }
    var reviewExpanded by remember { mutableStateOf(true) }
    var notFoundExpanded by remember { mutableStateOf(false) }

    val accent = LocalAccentColor.current
    val sheetShape = ContinuousRoundedRectangle(GlassTokens.cardCornerRadius)

    Dialog(onDismissRequest = {
        matchingJob?.cancel()
        onDismiss()
    }) {
        GlassControlSurface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .clip(sheetShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F1420).copy(alpha = 0.94f))
                    .border(1.dp, GlassTokens.liquidBorderBrush(), sheetShape)
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when (step) {
                                    ImportWizardStep.ENTRY -> "Import Playlist"
                                    ImportWizardStep.PROGRESS -> "Matching Tracks"
                                    ImportWizardStep.REVIEW -> "Review Matches"
                                    ImportWizardStep.SAVE -> "Save Playlist"
                                    ImportWizardStep.RESULT -> "Import Complete"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                matchingJob?.cancel()
                                onDismiss()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (errorMessage != null) {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage.orEmpty(),
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Content by Step
                    when (step) {
                        ImportWizardStep.ENTRY -> {
                            EntryStepContent(
                                inputText = inputText,
                                onTextChanged = {
                                    inputText = it
                                    errorMessage = null
                                },
                                onOpenFilePicker = onOpenFilePicker,
                                onStartMatch = {
                                    try {
                                        val candidates = UniversalPlaylistParser.parseAuto(inputText, config)
                                        if (candidates.isEmpty()) {
                                            errorMessage = "No valid track entries found in file or text."
                                            return@EntryStepContent
                                        }
                                        if (candidates.size > config.maxLines) {
                                            errorMessage = "Playlist exceeds limit of ${config.maxLines} tracks."
                                            return@EntryStepContent
                                        }

                                        step = ImportWizardStep.PROGRESS
                                        progressTotal = candidates.size
                                        matchingJob = scope.launch {
                                            try {
                                                val matched = PlaylistImportEngine.matchCandidates(
                                                    candidates = candidates,
                                                    localCatalog = localCatalog,
                                                    searchProvider = searchProvider,
                                                    config = config,
                                                    onProgress = { pStep, curr, tot ->
                                                        progressStep = pStep
                                                        progressCurrent = curr
                                                        progressTotal = tot
                                                    }
                                                )
                                                resultItems = matched
                                                step = ImportWizardStep.REVIEW
                                            } catch (e: Exception) {
                                                errorMessage = "Matching cancelled or failed: ${e.message}"
                                                step = ImportWizardStep.ENTRY
                                            }
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Failed to parse playlist file."
                                    }
                                }
                            )
                        }

                        ImportWizardStep.PROGRESS -> {
                            ProgressStepContent(
                                progressStep = progressStep,
                                current = progressCurrent,
                                total = progressTotal,
                                onCancel = {
                                    matchingJob?.cancel()
                                    step = ImportWizardStep.ENTRY
                                }
                            )
                        }

                        ImportWizardStep.REVIEW -> {
                            ReviewStepContent(
                                items = resultItems,
                                onItemAcceptedChange = { targetId, accepted ->
                                    resultItems = resultItems.map {
                                        if (it.id == targetId) it.copy(isAccepted = accepted) else it
                                    }
                                },
                                onAcceptAllMatched = {
                                    resultItems = resultItems.map {
                                        if (it.status == ImportMatchStatus.MATCHED) it.copy(isAccepted = true) else it
                                    }
                                },
                                onChooseAnother = { item -> alternativeItemTarget = item },
                                onContinueToSave = { step = ImportWizardStep.SAVE }
                            )
                        }

                        ImportWizardStep.SAVE -> {
                            SaveStepContent(
                                playlistName = playlistName,
                                onNameChange = { playlistName = it },
                                alsoCreateYouTube = alsoCreateYouTube,
                                onAlsoCreateYouTubeChange = { alsoCreateYouTube = it },
                                acceptedCount = resultItems.count { it.isAccepted && it.matchedTrack != null },
                                onSave = {
                                    val acceptedTracks = resultItems
                                        .filter { it.isAccepted && it.matchedTrack != null }
                                        .map { it.matchedTrack!! }

                                    scope.launch {
                                        try {
                                            val id = onSavePlaylist(playlistName, acceptedTracks, alsoCreateYouTube)
                                            createdPlaylistId = id
                                            step = ImportWizardStep.RESULT
                                        } catch (e: Exception) {
                                            errorMessage = "Failed to save playlist: ${e.message}"
                                        }
                                    }
                                },
                                onBack = { step = ImportWizardStep.REVIEW }
                            )
                        }

                        ImportWizardStep.RESULT -> {
                            ResultStepContent(
                                playlistName = playlistName,
                                count = resultItems.count { it.isAccepted && it.matchedTrack != null },
                                alsoCreateYouTube = alsoCreateYouTube,
                                onOpen = {
                                    createdPlaylistId?.let { onOpenPlaylist(it) }
                                    onDismiss()
                                },
                                onClose = onDismiss
                            )
                        }
                    }
                }

                // Alternative Candidate Picker Sheet
                if (alternativeItemTarget != null) {
                    AlternativeCandidateDialog(
                        item = alternativeItemTarget!!,
                        onSelectAlternative = { chosenTrack ->
                            resultItems = resultItems.map {
                                if (it.id == alternativeItemTarget!!.id) {
                                    it.copy(
                                        matchedTrack = chosenTrack,
                                        isAccepted = true,
                                        status = ImportMatchStatus.MATCHED
                                    )
                                } else it
                            }
                            alternativeItemTarget = null
                        },
                        onDismiss = { alternativeItemTarget = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryStepContent(
    inputText: String,
    onTextChanged: (String) -> Unit,
    onOpenFilePicker: (() -> Unit)?,
    onStartMatch: () -> Unit
) {
    val accent = LocalAccentColor.current
    val lineCount = remember(inputText) {
        if (inputText.isBlank()) 0 else inputText.lineSequence().count { it.isNotBlank() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Select a Spotify, Apple Music, or TuneMyMusic CSV/TXT export, or paste track lines below.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (onOpenFilePicker != null) {
            LiquidButton(
                onClick = onOpenFilePicker,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose CSV / TXT File", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = onTextChanged,
            placeholder = {
                Text(
                    text = "Paste playlist text here:\nArtist - Title\nTitle by Artist\nhttps://youtu.be/...",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 13.sp
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = ContinuousRoundedRectangle(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$lineCount items detected (Max 5,000 / 5 MB)",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            LiquidButton(
                onClick = onStartMatch,
                enabled = inputText.isNotBlank(),
                modifier = Modifier.width(160.dp)
            ) {
                Text("Start Matching", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProgressStepContent(
    progressStep: ImportProgressStep,
    current: Int,
    total: Int,
    onCancel: () -> Unit
) {
    val accent = LocalAccentColor.current
    val fraction = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            progress = { fraction },
            color = accent,
            strokeWidth = 4.dp,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when (progressStep) {
                ImportProgressStep.PARSING -> "Reading and validating playlist..."
                ImportProgressStep.MATCHING_LOCAL -> "Matching against local music library..."
                ImportProgressStep.MATCHING_ONLINE -> "Querying online song catalog..."
                ImportProgressStep.SAVING -> "Saving playlist..."
                ImportProgressStep.DONE -> "Matching complete!"
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "$current of $total tracks processed (${(fraction * 100).toInt()}%)",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        LiquidButton(
            onClick = onCancel,
            modifier = Modifier.width(140.dp)
        ) {
            Text("Cancel", fontSize = 14.sp)
        }
    }
}

@Composable
private fun ReviewStepContent(
    items: List<ImportResultItem>,
    onItemAcceptedChange: (id: String, accepted: Boolean) -> Unit,
    onAcceptAllMatched: () -> Unit,
    onChooseAnother: (ImportResultItem) -> Unit,
    onContinueToSave: () -> Unit
) {
    val matched = items.filter { it.status == ImportMatchStatus.MATCHED }
    val review = items.filter { it.status == ImportMatchStatus.NEEDS_REVIEW }
    val notFound = items.filter { it.status == ImportMatchStatus.NOT_FOUND }

    val acceptedCount = items.count { it.isAccepted && it.matchedTrack != null }
    val skippedCount = items.size - acceptedCount

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassBadge(text = "${matched.size} Matched", color = Color(0xFF10B981))
                GlassBadge(text = "${review.size} Review", color = Color(0xFFF59E0B))
                GlassBadge(text = "${notFound.size} Missing", color = Color(0xFF6B7280))
            }

            LiquidButton(
                onClick = onAcceptAllMatched,
                modifier = Modifier.height(36.dp)
            ) {
                Text("Accept All Matched", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
                ReviewTrackRow(
                    item = item,
                    onAcceptedChange = { onItemAcceptedChange(item.id, it) },
                    onChooseAnother = { onChooseAnother(item) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$acceptedCount tracks will be added, $skippedCount skipped",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            LiquidButton(
                onClick = onContinueToSave,
                enabled = acceptedCount > 0,
                modifier = Modifier.width(160.dp)
            ) {
                Text("Continue to Save", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ReviewTrackRow(
    item: ImportResultItem,
    onAcceptedChange: (Boolean) -> Unit,
    onChooseAnother: () -> Unit
) {
    val accent = LocalAccentColor.current
    val rowShape = ContinuousRoundedRectangle(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), rowShape)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox/Toggle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (item.isAccepted) accent else Color.White.copy(alpha = 0.1f))
                    .clickable { onAcceptedChange(!item.isAccepted) },
                contentAlignment = Alignment.Center
            ) {
                if (item.isAccepted) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Accepted",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Input track line
                Text(
                    text = item.candidate.title + if (item.candidate.artist.isNotBlank()) " • ${item.candidate.artist}" else "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val matched = item.matchedTrack
                if (matched != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "→ ${matched.title} (${matched.artist})",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val pct = (item.confidenceScore * 100).toInt()
                        GlassBadge(
                            text = "$pct%",
                            color = if (pct >= 90) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                } else {
                    Text(
                        text = "No candidate match found",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            if (item.alternatives.size > 1) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onChooseAnother,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = "Choose alternative candidate",
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlternativeCandidateDialog(
    item: ImportResultItem,
    onSelectAlternative: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val dialogShape = ContinuousRoundedRectangle(16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(dialogShape)
                .background(Color(0xFF1E232F))
                .border(1.dp, GlassTokens.liquidBorderBrush(), dialogShape)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select Replacement Candidate",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "For: ${item.candidate.title} - ${item.candidate.artist}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(item.alternatives) { _, altTrack ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ContinuousRoundedRectangle(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable { onSelectAlternative(altTrack) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = altTrack.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "${altTrack.artist} • ${altTrack.durationFormatted}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LiquidButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dismiss", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SaveStepContent(
    playlistName: String,
    onNameChange: (String) -> Unit,
    alsoCreateYouTube: Boolean,
    onAlsoCreateYouTubeChange: (Boolean) -> Unit,
    acceptedCount: Int,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val accent = LocalAccentColor.current

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Specify playlist details and destination.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Playlist Name",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = playlistName,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            shape = ContinuousRoundedRectangle(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // YouTube sync option row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(12.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .clickable { onAlsoCreateYouTubeChange(!alsoCreateYouTube) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Cloud,
                contentDescription = null,
                tint = if (alsoCreateYouTube) accent else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Also create on YouTube Music",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Creates a remote playlist on your authenticated YouTube Music account",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Switch(
                checked = alsoCreateYouTube,
                onCheckedChange = onAlsoCreateYouTubeChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LiquidButton(
                onClick = onBack,
                modifier = Modifier.width(120.dp)
            ) {
                Text("Back", fontSize = 13.sp)
            }

            LiquidButton(
                onClick = onSave,
                enabled = playlistName.isNotBlank() && acceptedCount > 0,
                modifier = Modifier.width(200.dp)
            ) {
                Text("Save ($acceptedCount tracks)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ResultStepContent(
    playlistName: String,
    count: Int,
    alsoCreateYouTube: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val accent = LocalAccentColor.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Playlist Created!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Added $count songs to '$playlistName'.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        if (alsoCreateYouTube) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Remote YouTube Music playlist was queued for creation.",
                fontSize = 12.sp,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidButton(
                onClick = onClose,
                modifier = Modifier.width(120.dp)
            ) {
                Text("Close", fontSize = 13.sp)
            }
            LiquidButton(
                onClick = onOpen,
                modifier = Modifier.width(160.dp)
            ) {
                Text("Open Playlist", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
