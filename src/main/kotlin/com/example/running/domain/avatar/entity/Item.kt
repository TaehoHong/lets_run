package com.example.running.domain.avatar.entity

import com.example.running.domain.common.entity.CreatedDatetime
import jakarta.persistence.*

@Entity
class Item(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_type_id", nullable = false, columnDefinition = "TINYINT UNSIGNED", referencedColumnName = "id")
    val itemType: ItemType,

    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(128)")
    val name: String,

    @Column(name = "file_path", nullable = false, columnDefinition = "VARCHAR(64)")
    val filePath: String,

    @Column(name = "point", nullable = false, columnDefinition = "INT UNSIGNED")
    val point: Int,

): CreatedDatetime()