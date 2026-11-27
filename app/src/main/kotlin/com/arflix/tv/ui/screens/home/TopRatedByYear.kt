package com.arflix.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arflix.tv.R
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.repository.TOP_RATED_BY_YEAR_MIN_YEAR
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.TextSecondary

internal const val TOP_RATED_BY_YEAR_CATEGORY_ID = "top_rated_by_year"

internal fun topRatedYearTabs(selectedYear: Int, currentYear: Int): List<Int> =
    listOf(selectedYear - 1, selectedYear, selectedYear + 1)
        .filter { it in TOP_RATED_BY_YEAR_MIN_YEAR..currentYear }
        .distinct()

internal fun moveTopRatedYearCandidate(candidateYear: Int, increment: Int, currentYear: Int): Int =
    (candidateYear + increment).coerceIn(TOP_RATED_BY_YEAR_MIN_YEAR, currentYear)

internal fun withTopRatedByYearRow(
    categories: List<Category>,
    items: List<MediaItem>
): List<Category> {
    val withoutRow = categories.filterNot { it.id == TOP_RATED_BY_YEAR_CATEGORY_ID }.toMutableList()
    val lastTrendingIndex = withoutRow.indexOfLast {
        it.id == "trending_movies" || it.id == "trending_tv" || it.id == "trending_series" || it.id == "trending_anime"
    }
    val insertionIndex = if (lastTrendingIndex >= 0) lastTrendingIndex + 1 else withoutRow.size.coerceAtMost(3)
    withoutRow.add(
        insertionIndex,
        Category(
            id = TOP_RATED_BY_YEAR_CATEGORY_ID,
            title = "Top Rated by Year",
            items = items
        )
    )
    return withoutRow
}

@Composable
internal fun TopRatedByYearSelector(
    selectedYear: Int,
    currentYear: Int,
    isFocused: Boolean,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    val selectorDescription = stringResource(R.string.home_top_rated_by_year_selector)
    var localExpanded by remember { mutableStateOf(false) }
    val menuExpanded = expanded ?: localExpanded
    fun setExpanded(value: Boolean) {
        if (expanded == null) localExpanded = value
        onExpandedChange?.invoke(value)
    }
    Box(
        modifier = modifier
            .testTag("top_rated_by_year_selector")
            .semantics { contentDescription = selectorDescription },
    ) {
        val shape = RoundedCornerShape(8.dp)
        Row(
            modifier = Modifier
                .testTag("top_rated_by_year_dropdown")
                .background(Color.White.copy(alpha = if (isFocused) 0.18f else 0.1f), shape)
                .then(if (isFocused) Modifier.border(2.dp, Color(0xFFFFCD3C), shape) else Modifier)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.DirectionCenter)
                    ) {
                        setExpanded(!menuExpanded)
                        true
                    } else false
                }
                .clickable { setExpanded(!menuExpanded) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedYear.toString(),
                style = ArflixTypography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Text(text = "▾", style = ArflixTypography.label, color = Color.White)
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { setExpanded(false) },
            modifier = Modifier.heightIn(max = 360.dp).widthIn(min = 112.dp)
                .verticalScroll(rememberScrollState())
        ) {
            (currentYear downTo TOP_RATED_BY_YEAR_MIN_YEAR).forEach { year ->
                DropdownMenuItem(
                    modifier = Modifier.testTag("top_rated_by_year_$year"),
                    text = {
                        Text(
                            text = year.toString(),
                            fontWeight = if (year == selectedYear) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        setExpanded(false)
                        onYearSelected(year)
                    }
                )
            }
        }
    }
}

@Composable
internal fun TopRatedByYearEmptyMessage(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.home_top_rated_by_year_empty),
        style = ArflixTypography.body,
        color = TextSecondary,
        modifier = modifier
            .testTag("top_rated_by_year_empty")
            .padding(vertical = 22.dp)
    )
}
