package com.opennavi.ui.offlinemaps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opennavi.domain.model.CountryOfflineMap
import com.opennavi.domain.model.OfflineMapState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menedżer Map Offline (OpenStreetMap dla wybranych państw) z:
 * - Wyszukiwarką państw (po nazwie państwa, kodzie ISO lub regionie górskim)
 * - Filtrem "Wszystkie / Tylko pobrane"
 * - Pobieraniem całych państw do pamięci lokalnej oraz aktualizacją nowych wersji OSM
 */
@Composable
fun OfflineMapsManagerSheet(
    countries: List<CountryOfflineMap>,
    onDownloadOrUpdateCountry: (String) -> Unit,
    onCheckAllUpdates: () -> Unit,
    onDeleteCountryMap: (String) -> Unit,
    onFlyToCountry: (CountryOfflineMap) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyDownloaded by remember { mutableStateOf(false) }

    val downloadedCount = countries.count {
        it.state == OfflineMapState.DOWNLOADED_UP_TO_DATE || it.state == OfflineMapState.UPDATE_AVAILABLE
    }
    val updatesCount = countries.count { it.state == OfflineMapState.UPDATE_AVAILABLE }

    val filteredCountries = remember(countries, searchQuery, showOnlyDownloaded) {
        val query = searchQuery.trim().lowercase(Locale.getDefault())
        countries.filter { country ->
            val matchesDownloaded = !showOnlyDownloaded ||
                country.state == OfflineMapState.DOWNLOADED_UP_TO_DATE ||
                country.state == OfflineMapState.UPDATE_AVAILABLE ||
                country.state == OfflineMapState.DOWNLOADING

            val matchesSearch = query.isEmpty() ||
                country.countryNamePl.lowercase(Locale.getDefault()).contains(query) ||
                country.countryCode.lowercase(Locale.getDefault()).contains(query) ||
                country.regionName.lowercase(Locale.getDefault()).contains(query)

            matchesDownloaded && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Mapy Offline OpenStreetMap",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Katalog: ${countries.size} państw  •  Pobrane: $downloadedCount  •  Aktualizacje: $updatesCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(onClick = onCheckAllUpdates) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sprawdź aktualizacje",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Aktualizacje")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Wyszukiwarka państw
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Szukaj państwa, kodu (np. PL, SK) lub pasma (np. Alpy, Tatry)...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Szukaj państwa"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Wyczyść wyszukiwanie"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Pasek szybkiego filtrowania
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !showOnlyDownloaded,
                onClick = { showOnlyDownloaded = false },
                label = { Text("Wszystkie państwa (${countries.size})") }
            )
            FilterChip(
                selected = showOnlyDownloaded,
                onClick = { showOnlyDownloaded = true },
                label = { Text("Pobrane lokalnie ($downloadedCount)") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredCountries.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Nie znaleziono państwa pasującego do zapytania \"$searchQuery\".",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredCountries, key = { it.countryCode }) { country ->
                    CountryMapCard(
                        country = country,
                        onDownloadOrUpdate = { onDownloadOrUpdateCountry(country.countryCode) },
                        onDelete = { onDeleteCountryMap(country.countryCode) },
                        onFlyTo = { onFlyToCountry(country.copy()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryMapCard(
    country: CountryOfflineMap,
    onDownloadOrUpdate: () -> Unit,
    onDelete: () -> Unit,
    onFlyTo: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${country.countryNamePl} (${country.countryCode})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${country.regionName} • ~${country.estimatedSizeMB} MB (OSM)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onFlyTo) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Pokaż państwo na mapie"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (country.state) {
                OfflineMapState.NOT_DOWNLOADED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mapa niepobrana (tryb online)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onDownloadOrUpdate) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pobierz państwo")
                        }
                    }
                }

                OfflineMapState.DOWNLOADING -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pobieranie i indeksowanie kafelków OSM...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${country.downloadProgressPercent}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { country.downloadProgressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OfflineMapState.DOWNLOADED_UP_TO_DATE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val updatedDate = if (country.lastUpdatedEpochMillis > 0) {
                                dateFormat.format(Date(country.lastUpdatedEpochMillis))
                            } else "Dzisiaj"
                            Text(
                                text = "Zapisano lokalnie (Aktualna • $updatedDate)",
                                fontSize = 12.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Usuń mapę z pamięci",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                OfflineMapState.UPDATE_AVAILABLE -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Dostępna nowa wersja mapy: ${country.remoteVersionTag}",
                            fontSize = 12.sp,
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onDownloadOrUpdate,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF6C00)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdateAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aktualizuj mapę państwa")
                            }
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Usuń mapę",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                OfflineMapState.ERROR -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Błąd zapisu mapy",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                        Button(onClick = onDownloadOrUpdate) {
                            Text("Ponów próbę")
                        }
                    }
                }
            }
        }
    }
}
