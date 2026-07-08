package com.corunling.noteeverything.ui.time

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corunling.noteeverything.data.entity.SoftwareEntity
import com.corunling.noteeverything.ui.theme.CategoryColors
import kotlinx.coroutines.launch

data class FilterState(
    val selectedSoftwareIds: Set<Long> = emptySet(),
    val selectedCategories: Set<String> = emptySet()
) {
    val isActive: Boolean get() = selectedSoftwareIds.isNotEmpty() || selectedCategories.isNotEmpty()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    softwareList: List<SoftwareEntity>,
    currentFilter: FilterState,
    onApply: (FilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val allCategories = remember(softwareList) {
        softwareList.map { it.category }.distinct().sorted()
    }

    var selectedIds by remember { mutableStateOf(currentFilter.selectedSoftwareIds) }
    // 已激活的分类 chip（取消单个勾选不影响 chip 状态）
    var activatedCats by remember { mutableStateOf(currentFilter.selectedCategories) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .heightIn(max = 520.dp)
        ) {
            // ── 分类 Chip ──
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedIds.size == softwareList.size,
                    onClick = {
                        val allIds = softwareList.map { it.id }.toSet()
                        if (selectedIds.size == softwareList.size) {
                            selectedIds = emptySet(); activatedCats = emptySet()
                        } else {
                            selectedIds = allIds; activatedCats = emptySet()
                        }
                    },
                    label = { Text("全部", style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors
                )
                allCategories.forEach { category ->
                    val catIds = softwareList.filter { it.category == category }.map { it.id }.toSet()
                    val isActive = category in activatedCats
                    FilterChip(
                        selected = isActive,
                        onClick = {
                            if (isActive) {
                                activatedCats = activatedCats - category
                                selectedIds = selectedIds - catIds
                            } else {
                                activatedCats = activatedCats + category
                                selectedIds = selectedIds + catIds
                            }
                        },
                        label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                        colors = chipColors
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 软件列表 ──
            val filtered = if (activatedCats.isEmpty()) softwareList
            else softwareList.filter { it.category in activatedCats }

            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filtered, key = { it.id }) { software ->
                    val checked = software.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIds = if (checked) selectedIds - software.id
                                else selectedIds + software.id
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selectedIds = if (checked) selectedIds - software.id
                                else selectedIds + software.id
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = CategoryColors.forCategory(software.category).primary
                        ) {}
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = software.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onApply(FilterState(selectedSoftwareIds = selectedIds, selectedCategories = activatedCats))
                    scope.launch { sheetState.hide() }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确定", fontWeight = FontWeight.Medium)
            }
        }
    }
}
