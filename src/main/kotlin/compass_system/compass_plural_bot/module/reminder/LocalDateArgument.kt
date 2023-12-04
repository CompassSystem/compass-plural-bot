package compass_system.compass_plural_bot.module.reminder

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Argument
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.converters.SingleConverter
import com.kotlindiscord.kord.extensions.commands.converters.Validator
import com.kotlindiscord.kord.extensions.commands.converters.builders.ConverterBuilder
import com.kotlindiscord.kord.extensions.parser.StringParser
import dev.kord.core.entity.interaction.OptionValue
import dev.kord.core.entity.interaction.StringOptionValue
import dev.kord.rest.builder.interaction.OptionsBuilder
import dev.kord.rest.builder.interaction.StringChoiceBuilder
import kotlinx.datetime.LocalDate
import java.lang.NumberFormatException

fun Arguments.localDate(
	body: LocalDateConverterBuilder.() -> Unit
): SingleConverter<LocalDate> {
	val builder = LocalDateConverterBuilder()

	body(builder)

	builder.validateArgument()

	return builder.build(this)
}

class LocalDateConverter(
	override var validator: Validator<LocalDate> = null
) : SingleConverter<LocalDate>() {
	override val signatureTypeString: String = "local date"
	override val showTypeInSignature: Boolean = false

	override suspend fun parse(parser: StringParser?, context: CommandContext, named: String?): Boolean {
		val arg: String = named ?: parser?.parseNext()?.data ?: return false

		parsed = convertString(arg)

		return true
	}

	override suspend fun toSlashOption(arg: Argument<*>): OptionsBuilder =
		StringChoiceBuilder(arg.displayName, arg.description).apply {
			this@apply.maxLength = 3 + 3 + 4
			this@apply.minLength = 3 + 3 + 2

			required = true
		}

	override suspend fun parseOption(context: CommandContext, option: OptionValue<*>): Boolean {
		val optionValue = (option as? StringOptionValue)?.value ?: return false

		parsed = convertString(optionValue)

		return true
	}

	private fun convertString(value: String): LocalDate {
		// todo: support ISO-8601 dates
		if (value.length < 3 + 3 + 2) {
			throw DiscordRelayedException("Too short, date must be in the format DD/MM/[YY]YY.")
		} else if (value.length > 3 + 3 + 4) {
			throw DiscordRelayedException("Too long, date must be in the format DD/MM/[YY]YY.")
		}

		return try {
			val parts = value.split("/").map { it.toInt() }

			if (parts.size != 3) {
				throw DiscordRelayedException("Date must be in the format DD/MM/[YY]YY.")
			}

			val year = if (parts[2] < 100) {
				2000 + parts[2]
			} else {
				parts[2]
			}

			LocalDate(year, parts[1], parts[0])
		} catch (e: NumberFormatException) {
			throw DiscordRelayedException("Part of date is not a number")
		} catch (e: IllegalArgumentException) {
			throw DiscordRelayedException("Day, month, or year is invalid")
		}
	}
}

class LocalDateConverterBuilder : ConverterBuilder<LocalDate>() {
	override fun build(arguments: Arguments): SingleConverter<LocalDate> {
		return arguments.arg(
			displayName = name,
			description = description,

			converter = LocalDateConverter().withBuilder(this)
		)
	}
}
