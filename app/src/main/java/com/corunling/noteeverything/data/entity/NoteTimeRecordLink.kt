// ============================================================
// NoteTimeRecordLink.kt — 笔记与时长记录的多对多关联表
// ============================================================
// 一条笔记可以关联多条时长记录（默认关联当天该软件的全部时长），
// 一条时长记录也可以被多条笔记引用。
//
// unique = true 确保同一对关系不会重复插入。

package com.corunling.noteeverything.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_time_record_link",
    indices = [
        Index(value = ["noteId", "timeRecordId"], unique = true)
    ]
)
data class NoteTimeRecordLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val timeRecordId: Long
)
