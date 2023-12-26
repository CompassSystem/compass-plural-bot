package compass_system.compass_plural_bot.module.headmate_store

import com.kotlindiscord.kord.extensions.DISCORD_BLURPLE
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.components.forms.ModalForm
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import compass_system.compass_plural_bot.module.pluralkit.PartialMember
import compass_system.compass_plural_bot.module.pluralkit.PluralKitExtension
import compass_system.compass_plural_bot.module.pluralkit.PluralKitModule
import dev.kord.common.Color
import dev.kord.rest.builder.message.embed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

class HeadmateStoreExtension : Extension() {
	override val name: String = HeadmateStoreModule.getId()

	private val pluralKit by lazy { bot.extensions[PluralKitModule.getId()] as PluralKitExtension }

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = "headmate-store"
			description = "Headmate store commands."

			ephemeralSubCommand(::HeadmateArgs) {
				name = "export"
				description = "Exports a headmate from PluralKit to the store."

				action { exportHeadmate() }
			}

			ephemeralSubCommand(::HeadmateArgs) {
				name = "restore"
				description = "Restores a headmate from the store to PluralKit."

				action { restoreHeadmate() }
			}

			ephemeralSubCommand(::HeadmateArgs) {
				name = "display"
				description = "Display a headmate from the store."

				action { displayHeadmate() }
			}

			ephemeralSubCommand {
				name = "list"
				description = "List all headmates in the store."

				action { listHeadmates() }
			}
		}
	}

	private suspend fun EphemeralSlashCommandContext<HeadmateArgs, ModalForm>.exportHeadmate() {
		val system = pluralKit.getSystem()
		val headmateName = arguments.name

		val headmate = system.members.firstOrNull { it.name == headmateName } ?: return respond { content = "$headmateName is not present in the PluralKit system." }.let {  }

		try {
		    pluralKit.deleteMember(headmate.id)
		} catch (e: IllegalStateException) {
			return respond { content = "Failed to delete $headmateName received ${e.message}." }.let {  }
		}

		system.members.removeIf { it.name == headmateName }

		val path = Path.of("headmates", "$headmateName.json")
		val data = PluralKitModule.json.encodeToString(PartialMember.serializer(), headmate.asPartial())

		withContext(Dispatchers.IO) {
			Files.newBufferedWriter(path, Charsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
		}.use {
			it.write(data)
			it.flush()
		}

		respond { content = "Exported $headmateName into the store." }
	}

	private suspend fun EphemeralSlashCommandContext<HeadmateArgs, ModalForm>.restoreHeadmate() {
		val system = pluralKit.getSystem()
		val headmateName = arguments.name

		system.members.forEach {
			if (it.name == headmateName) {
				return respond { content = "$headmateName already exists in the PluralKit system." }.let {  }
			}
		}

		val headmate = loadHeadmate(headmateName) ?: return respond { content = "$headmateName is not present in the store(headmates/${headmateName}.json)." }.let {  }

		try {
			val createdHeadmate = pluralKit.createMember(headmate)

			system.members.add(createdHeadmate)

			respond { content = "Restored ${headmateName}, their id is now `${createdHeadmate.id}`." }
		} catch (e: IllegalStateException) {
			respond { content = "Failed to restore $headmateName received ${e.message}." }
		}
	}

	private suspend fun EphemeralSlashCommandContext<HeadmateArgs, ModalForm>.displayHeadmate() {
		val headmateName = arguments.name
		val headmate = loadHeadmate(headmateName) ?: return respond { content = "Headmate $headmateName not found." }.let {  }

		val headmateTitle = headmate.displayName?.let {
			val takeLength = headmate.pronouns?.length?.plus(3) ?: 0

			if (takeLength >= it.length) {
				it.substring(0, it.lastIndexOf(' '))
			} else {
				it.take(it.length - takeLength)
			}

		} ?: headmate.name

		// todo: more information?
		respond {
			embed {
				title = headmateTitle
				color = headmate.color?.let { Color(it.toInt(16)) } ?: DISCORD_BLURPLE
				image = "https://raw.githubusercontent.com/CompassSystem/headmate-labeller/main/resources/filler.png"

				thumbnail {
					url = headmate.avatarUrl ?: "https://discord.com/assets/5d6a5e9d7d77ac29116e.png"
				}

				if (headmateTitle != headmate.name) {
					field {
						name = "Name"
						value = headmate.name
						inline = true
					}
				}

				headmate.pronouns?.let {
					field {
						name = "Pronouns"
						value = it
						inline = true
					}
				}

				headmate.proxyTags.let {
					if (it.isNotEmpty()) {
						field {
							name = "Proxy Tags"
							value = it.joinToString("\n") { proxy -> "`$proxy`" }
							inline = true
						}
					}
				}
			}
		}
	}

	private suspend fun EphemeralSlashCommandContext<Arguments, ModalForm>.listHeadmates() {
		val system = pluralKit.getSystem()

		val headmatesInSystem = system.members.map { it.name }
		val headmatesInStore = withContext(Dispatchers.IO) { Files.list(Path.of("headmates")) }.use {
			files -> files
				.filter { it.extension == "json" }
				.map { it.nameWithoutExtension }
				.filter { it !in headmatesInSystem }
				.toList()
		}

		val response = buildString {
			appendLine("**Headmates in PluralKit:**")
			appendLine(headmatesInSystem.sorted().joinToString(", "))
			appendLine()
			appendLine("**Headmates in LocalStore:**")
			appendLine(headmatesInStore.sorted().joinToString(", "))
		}

		respond {
			content = response
		}
	}

	private fun loadHeadmate(name: String): PartialMember? {
		val path = Path.of("headmates", "$name.json")

		if (!path.toFile().exists()) {
			return null
		}

		return PluralKitModule.json.decodeFromString<PartialMember>(path.readText(Charsets.UTF_8))
	}
}

class HeadmateArgs : Arguments() {
	val name by string {
		name = "name"
		description = "Name of the headmate."
	}
}
