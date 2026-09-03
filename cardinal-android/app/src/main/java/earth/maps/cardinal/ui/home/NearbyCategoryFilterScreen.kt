/*
 *     Cardinal Maps
 *     Copyright (C) 2025 Cardinal Maps Authors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package earth.maps.cardinal.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.R.drawable
import earth.maps.cardinal.R.string

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyCategoryFilterScreen(
    viewModel: NearbyViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.beginCategoryFilterEdit()
    }

    val searchQuery by viewModel.draftCategorySearchQuery.collectAsState()

    val filteredCategories = if (searchQuery.isBlank()) {
        viewModel.categoryFilters
    } else {
        val query = searchQuery.trim().lowercase()
        viewModel.categoryFilters.filter { category ->
            val categoryLabel = stringResource(category.labelResource)
            categoryLabel.lowercase().contains(query) ||
            category.subcategories.any { subcategory ->
                stringResource(subcategory.labelResource).lowercase().contains(query)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(string.nearby_categories_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(drawable.ic_arrow_back),
                            contentDescription = stringResource(string.back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetDraftCategoryFilters() }) {
                        Text(text = stringResource(string.reset_filters))
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    viewModel.applyCategoryFilters()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(dimen.padding))
            ) {
                Icon(
                    painter = painterResource(drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp)
                )
                Text(text = stringResource(string.apply_selection))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = dimensionResource(dimen.padding),
                top = dimensionResource(dimen.padding_minor),
                end = dimensionResource(dimen.padding),
                bottom = dimensionResource(dimen.padding)
            ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(dimen.padding))
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::updateDraftCategorySearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(drawable.ic_search),
                            contentDescription = null
                        )
                    },
                    placeholder = {
                        Text(text = stringResource(string.search_categories_or_keywords))
                    }
                )
            }

            items(filteredCategories, key = { it.category }) { category ->
                NearbyCategoryCard(
                    category = category,
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NearbyCategoryCard(
    category: NearbyCategorySpec,
    viewModel: NearbyViewModel
) {
    val selectedCategories by viewModel.draftSelectedCategories.collectAsState()
    val categoryLabel = stringResource(category.labelResource)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(dimen.padding))
        ) {
            Text(
                text = categoryLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                modifier = Modifier.padding(top = dimensionResource(dimen.padding_minor)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NearbyCategoryChip(
                    category = category.category,
                    label = stringResource(string.category_all, categoryLabel),
                    selected = selectedCategories.contains(category.category),
                    onClick = viewModel::toggleDraftCategorySelection
                )
                category.subcategories.forEach { subcategory ->
                    NearbyCategoryChip(
                        category = subcategory.category,
                        label = stringResource(subcategory.labelResource),
                        selected = selectedCategories.contains(subcategory.category),
                        onClick = viewModel::toggleDraftCategorySelection
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyCategoryChip(
    category: String,
    label: String,
    selected: Boolean,
    onClick: (String) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { onClick(category) },
        label = { Text(text = label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        }
    )
}
