package compass_system.compass_plural_bot

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.adapters.mongodb.kordExCodecRegistry
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.loadModule
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import compass_system.compass_plural_bot.database.*
import compass_system.compass_plural_bot.module.BotModule
import compass_system.compass_plural_bot.module.headmate_labeller.HeadmateLabellerModule
import compass_system.compass_plural_bot.module.headmate_store.HeadmateStoreModule
import compass_system.compass_plural_bot.module.pluralkit.PluralKitModule
import compass_system.compass_plural_bot.module.reminder.ReminderModule
import kotlinx.coroutines.flow.firstOrNull
import org.koin.dsl.bind
import org.bson.codecs.configuration.CodecRegistries

object Main {
	private val DISCORD_TOKEN = env("DISCORD_TOKEN")
	private val TEST_GUILD = envOrNull("TEST_GUILD")
	private val MONGODB_CONNECTION_URI = env("MONGODB_CONNECTION_URI")

	private lateinit var client: MongoClient

	suspend fun main(args: Array<String>) {
		val database = createDatabase()

		val modules: List<BotModule> = listOf(ReminderModule, PluralKitModule, HeadmateLabellerModule, HeadmateStoreModule)

		val metadata = database.getCollection<Metadata>("metadata")

		modules.forEach {
			val filter = Filters.eq("id", it.getId())
			val existingMetadata = metadata.find(filter).firstOrNull() ?: Metadata(it.getId(), 0u)
			val newVersion = it.applyDatabaseMigrations(database, existingMetadata.version)

			if (newVersion != 0u) {
				metadata.findOneAndReplace(filter, existingMetadata.copy(version = newVersion), FindOneAndReplaceOptions().upsert(true))
			}
		}

		val bot = ExtensibleBot(DISCORD_TOKEN) {
			applicationCommands {
				defaultGuild(TEST_GUILD)
			}

			hooks {
				beforeKoinSetup {
					loadModule {
						single { database } bind MongoDatabase::class
					}
				}

				kordShutdownHook
			}

			for (module in modules) {
				module.applyToBot(this)
			}
		}

		bot.start()
	}

	private fun createDatabase(): MongoDatabase {
		val registry = CodecRegistries.fromRegistries(
			MongoClientSettings.getDefaultCodecRegistry(),
			kordExCodecRegistry,
			CodecRegistries.fromCodecs(
				UIntCodec,
				LocalDateCodec
			)
		)

		val settings = MongoClientSettings
			.builder()
			.codecRegistry(registry)
			.applyConnectionString(ConnectionString(MONGODB_CONNECTION_URI))
			.build()

		client = MongoClient.create(settings)

		val instance = client.getDatabase("compass_plural_bot")

		Runtime.getRuntime().addShutdownHook(Thread {
			println("Shutting down MongoDB client.")
			client.close()
		})

		return instance
	}

//	suspend fun runAfter(currentVersion: UInt, database: MongoDatabase, onMigrated: suspend (version: UInt) -> Unit) {
//		// todo: use KSP instead for creating the Map<UInt, (MongoDatabase) -> Unit>
//		val migrations = Migrations::class.declaredFunctions
//			.filter { it.name.startsWith("v") }
//			.associateBy { it.name.substring(1).toUInt() }
//			.toSortedMap()
//			.tailMap(currentVersion + 1u)
//
//		if (migrations.isNotEmpty()) {
//			migrations.values.forEach { it.callSuspend(Migrations, database) }
//
//			onMigrated.invoke(migrations.lastKey())
//		}
//	}
}
