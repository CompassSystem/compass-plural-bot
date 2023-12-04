package compass_system.compass_plural_bot.module

import com.kotlindiscord.kord.extensions.builders.ExtensibleBotBuilder
import com.mongodb.kotlin.client.coroutine.MongoDatabase

interface BotModule {
	fun getId(): String
	suspend fun applyDatabaseMigrations(database: MongoDatabase, version: UInt): UInt
	suspend fun applyToBot(bot: ExtensibleBotBuilder)
}
