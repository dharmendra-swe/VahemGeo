package com.vahemgeo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vahemgeo.app.data.local.BookmarkEntity
import com.vahemgeo.app.data.local.HistoryEntity
import com.vahemgeo.app.ui.theme.CyberTeal
import com.vahemgeo.app.ui.theme.ElectricLime
import com.vahemgeo.app.ui.theme.MutedSage
import com.vahemgeo.app.ui.theme.PolarWhite
import com.vahemgeo.app.ui.theme.SurfaceCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    historyLogs: List<HistoryEntity>,
    currentLat: Double,
    currentLng: Double,
    onDismiss: () -> Unit,
    onSelectLocation: (Double, Double) -> Unit,
    onAddBookmark: (String, Double, Double, String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onClearHistory: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    var labelInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceCard,
                contentColor = ElectricLime
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Bookmarks (${bookmarks.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    selectedContentColor = ElectricLime,
                    unselectedContentColor = MutedSage
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "History (${historyLogs.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    selectedContentColor = ElectricLime,
                    unselectedContentColor = MutedSage
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                // Bookmarks Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Favorites",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PolarWhite
                    )
                    Button(
                        onClick = { showAddDialog = !showAddDialog },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "+ Save Pin",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (showAddDialog) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberTeal),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("New Bookmark (${"%.4f".format(currentLat)}, ${"%.4f".format(currentLng)})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PolarWhite)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = labelInput,
                                onValueChange = { labelInput = it },
                                label = { Text("Label (e.g. Home, Office)", color = MutedSage, fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = notesInput,
                                onValueChange = { notesInput = it },
                                label = { Text("Notes (optional)", color = MutedSage, fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.align(Alignment.End)) {
                                TextButton(onClick = { showAddDialog = false }) {
                                    Text("Cancel", color = MutedSage)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (labelInput.isNotBlank()) {
                                            onAddBookmark(labelInput, currentLat, currentLng, notesInput)
                                            labelInput = ""
                                            notesInput = ""
                                            showAddDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime)
                                ) {
                                    Text("Save", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (bookmarks.isEmpty() && !showAddDialog) {
                    Text(
                        text = "No saved locations yet. Tap '+ Save Pin' to save the selected coordinates.",
                        color = MutedSage,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(bookmarks) { b ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onSelectLocation(b.latitude, b.longitude)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(containerColor = CyberTeal.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = ElectricLime)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(b.label, fontWeight = FontWeight.Bold, color = PolarWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${"%.5f".format(b.latitude)}, ${"%.5f".format(b.longitude)}", style = MaterialTheme.typography.bodySmall, color = MutedSage)
                                    if (b.notes.isNotBlank()) {
                                        Text(b.notes, style = MaterialTheme.typography.bodySmall, color = MutedSage.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { onDeleteBookmark(b) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            } else {
                // History Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Locations & Routes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PolarWhite
                    )
                    if (historyLogs.isNotEmpty()) {
                        TextButton(onClick = onClearHistory) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (historyLogs.isEmpty()) {
                    Text(
                        text = "No spoofing history recorded yet.",
                        color = MutedSage,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(historyLogs) { h ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onSelectLocation(h.latitude, h.longitude)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(containerColor = CyberTeal.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = ElectricLime)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (h.label.isNotBlank()) {
                                        Text(h.label, fontWeight = FontWeight.Bold, color = PolarWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${"%.5f".format(h.latitude)}, ${"%.5f".format(h.longitude)}", style = MaterialTheme.typography.bodySmall, color = MutedSage)
                                    } else {
                                        Text("${"%.5f".format(h.latitude)}, ${"%.5f".format(h.longitude)}", fontWeight = FontWeight.Bold, color = PolarWhite)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
