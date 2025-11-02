/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.inventory.data

import kotlinx.coroutines.flow.Flow

class OfflineItemsRepository(private  val itemDAO: ItemDAO) : ItemsRepository {
    override fun getAll(): Flow<List<Item>> {
        return itemDAO.findAll()
    }

    override fun getById(id: Int): Flow<Item?> {
        return itemDAO.findById(id)
    }

    override suspend fun insert(item: Item) {
        return itemDAO.insert(item)
    }

    override suspend fun insertItems(vararg items: Item) {
        itemDAO.insertItems(*items)
    }

    override suspend fun delete(item: Item) {
        itemDAO.delete(item)
    }

    override suspend fun deleteItems(items: List<Item>) {
       itemDAO.deleteItems(items)
    }

    override suspend fun update(item: Item) {
        itemDAO.update(item)
    }
}
