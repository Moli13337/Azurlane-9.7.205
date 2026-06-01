package com.azurlane.infra.database.repository

import com.azurlane.infra.database.table.CommanderStories
import mu.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

object StoryRepository {

    fun addStory(commanderId: Int, storyId: Int) {
        transaction {
            try {
                CommanderStories.insertIgnore {
                    it[CommanderStories.commanderId] = commanderId
                    it[CommanderStories.storyId] = storyId
                }
            } catch (e: Exception) {
                logger.warn(e) { "addStory failed" }
            }
        }
    }

    fun batchAddStories(commanderId: Int, storyIds: List<Int>) {
        transaction {
            for (storyId in storyIds) {
                try {
                    CommanderStories.insertIgnore {
                        it[CommanderStories.commanderId] = commanderId
                        it[CommanderStories.storyId] = storyId
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "batchAddStories failed for storyId=$storyId" }
                }
            }
        }
    }

    fun getStories(commanderId: Int): List<Int> = transaction {
        CommanderStories.selectAll()
            .where { CommanderStories.commanderId eq commanderId }
            .map { it[CommanderStories.storyId] }
    }

    fun hasStory(commanderId: Int, storyId: Int): Boolean = transaction {
        CommanderStories.selectAll()
            .where { (CommanderStories.commanderId eq commanderId) and (CommanderStories.storyId eq storyId) }
            .any()
    }
}
