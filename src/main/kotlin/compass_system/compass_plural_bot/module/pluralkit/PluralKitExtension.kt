package compass_system.compass_plural_bot.module.pluralkit

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.components.forms.ModalForm
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import dev.kord.common.entity.Permission
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import mu.KotlinLogging

class PluralKitExtension(private val pluralKitToken: String) : Extension() {
	override val name: String = PluralKitModule.getId()
	private val apiBaseUrl = "https://api.pluralkit.me/v2"
	private var system: PkSystem? = null
	private val logger = KotlinLogging.logger(name)

	val client = HttpClient(CIO) {
		defaultRequest {
			headers {
				append("Authorization", pluralKitToken)
				append("User-Agent", "compass-plural-bot/1.0.0")
			}
		}

		install(ContentNegotiation) {
			json(PluralKitModule.json)
		}
	}

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = "pluralbot"
			description = "PluralKit commands."

			check { hasPermission(Permission.Administrator) }

			ephemeralSubCommand {
				name = "refresh"
				description = "Refreshes the PluralKit cache."

				action { refreshPluralKitCache() }
			}
		}
	}

	suspend fun getSystem(): PkSystem {
		if (system == null) {
			system = getPluralKitSystem()
		}

		return system!!
	}

	private suspend fun EphemeralSlashCommandContext<Arguments, ModalForm>.refreshPluralKitCache() {
		system = try {
			getPluralKitSystem()
		} catch (e: Exception) {
			respond {
				content = "Failed to refresh the PluralKit system."
			}

			logger.error(e) { "Failed to refresh the PluralKit system." }

			return
		}

		respond {
			content = "Refreshed PluralKit system."
		}
	}

	private suspend fun getPluralKitSystem(): PkSystem {
		val system = client.get("${apiBaseUrl}/systems/@me").body<PkSystem>()
		val members = client.get("${apiBaseUrl}/systems/@me/members").body<List<PkMember>>()

		return PkSystem(
			system.id,
			system.name,
			system.tag,
			members.toMutableList(),
			system.avatarUrl
		)
	}

	suspend fun createMember(headmate: PartialMember): PkMember {
		val response = client.post("${apiBaseUrl}/members") {
			contentType(ContentType.Application.Json)
			setBody(headmate)
		}

		if (response.status == HttpStatusCode.OK) {
			return response.body()
		} else {
			throw IllegalStateException("${response.status}: ${response.body<String>()}")
		}
	}

	suspend fun deleteMember(id: String) {
		val response = client.delete("${apiBaseUrl}/members/${id}")

		if (response.status == HttpStatusCode.NoContent) {
			return
		} else {
			throw IllegalStateException("${response.status}: ${response.body<String>()}")
		}
	}
}
