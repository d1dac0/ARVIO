package com.arflix.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
    val selectorDescription = stringResource(R.string.home_top_rated_by_year_selector)
    Row(
        modifier = modifier
            .testTag("top_rated_by_year_selector")
            .semantics { contentDescription = selectorDescription },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        topRatedYearTabs(selectedYear, currentYear).forEach { year ->
            val selected = year == selectedYear
            val shape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .testTag("top_rated_by_year_$year")
                    .background(
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
                        shape = shape
                    )
                    .then(
                        if (selected && isFocused) {
                            Modifier.border(2.dp, Color(0xFFFFCD3C), shape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onYearSelected(year) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = year.toString(),
                    style = ArflixTypography.label.copy(
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected) Color.Black else Color.White
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
