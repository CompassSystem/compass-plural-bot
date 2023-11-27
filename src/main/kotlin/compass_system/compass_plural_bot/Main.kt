package compass_system.compass_plural_bot

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.adapters.mongodb.kordExCodecRegistry
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.loadModule
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import compass_system.compass_plural_bot.database.*
import compass_system.compass_plural_bot.extensions.ReminderExtension
import org.koin.dsl.bind
import org.bson.codecs.configuration.CodecRegistries

object Main {
	private val TOKEN = env("DISCORD_TOKEN")
	private val TEST_GUILD = envOrNull("TEST_GUILD")
	private val MONGODB_CONNECTION_URI = env("MONGODB_CONNECTION_URI")

	private lateinit var client: MongoClient

	suspend fun main(args: Array<String>) {
		val database = createDatabase()

		val bot = ExtensibleBot(TOKEN) {
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

			extensions {
				add(::ReminderExtension)
			}
		}

		bot.start()
	}

	private suspend fun createDatabase(): MongoDatabase {
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

		val currentVersion = instance.getSingleton("metadata", Metadata::class.java)?.version ?: 0u

		Migrations.runAfter(currentVersion, instance) {
			instance.upsertSingleton("metadata", Metadata::class.java, Metadata(it))
		}

		Runtime.getRuntime().addShutdownHook(Thread {
			println("Shutting down MongoDB client.")
			client.close()
		})

		return instance
	}
}
