package org.panchang.wire

import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.panchang.core.Paksha
import org.panchang.sampradaya.AbsenceReason
import org.panchang.sampradaya.EventGroup
import org.panchang.sampradaya.FastKind
import org.panchang.sampradaya.MahadvadashiType
import org.panchang.sampradaya.ObservanceAnchor
import org.panchang.sampradaya.ParanaBoundReason
import org.panchang.sampradaya.RuleConfidence
import org.panchang.sampradaya.VerificationStatus

/**
 * The version of the payload shape defined in this module.
 *
 * Three front doors will be pinned to it, so a rename here is a breaking change to all of them at
 * once. Bumping this is the deliberate act; quietly renaming a field is not.
 */
const val WIRE_SCHEMA_VERSION: Int = 1

/**
 * The `Json` configurations every emitter in this project uses.
 *
 * Held here rather than configured per front door, because two front doors with slightly
 * different `Json` settings produce payloads that differ in ways no test in either module can
 * see. The settings are chosen for diffability:
 *
 * - `encodeDefaults = true` so [WirePayload.schemaVersion] and other defaulted constants are
 *   actually present in the output rather than implied by their absence.
 * - `explicitNulls = false` so a value that is not there is *absent*, never the four characters
 *   `null` sitting where a time would go. This is what makes "an [EventTimeDto.Absent] carries no
 *   time-shaped field" a structural property of the schema rather than something each renderer
 *   has to remember.
 * - Strict decoding (unknown keys rejected) so a consumer pinned to v1 that meets a v2 payload
 *   fails loudly instead of silently dropping the field that changed.
 * - `classDiscriminator = "type"`, named once so no polymorphic type in the schema can pick a
 *   different one.
 */
object WireJson {

    /** For transport: `:api` responses, and anything measured in bytes on a wire. */
    val compact: Json = Json {
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
        prettyPrint = false
    }

    /**
     * For files: `:publish` output and `:calc`'s `--json`.
     *
     * A published artifact is read by humans and diffed by version control, and both of those go
     * badly with a single-line megabyte.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val pretty: Json = Json(compact) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
}

/**
 * Serialises an enum by its **declared name**, and refuses anything else.
 *
 * The domain enums live in `:sampradaya` and `:core` and are not annotated `@Serializable` — this
 * module does not get to reach in and annotate them. The obvious alternative, mirroring each enum
 * as a `:wire` copy, is the worse one: a constant added to the domain enum would still compile
 * against a stale mirror and only fail when a real value hit it. Serialising the domain enum
 * directly means there is nothing to keep in step.
 *
 * Deserialisation of an unknown name throws rather than falling back to a default. A default here
 * would turn "this build does not understand `PAKSAVARDHINI`" into "this is an ordinary
 * Ekadashi", which is a wrong answer wearing a right answer's clothes.
 */
abstract class EnumNameSerializer<T : Enum<T>>(
    private val serialName: String,
    private val values: Array<T>,
) : KSerializer<T> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): T {
        val raw = decoder.decodeString()
        return values.firstOrNull { it.name == raw } ?: throw SerializationException(
            "'$raw' is not a $serialName; expected one of " +
                values.joinToString(", ") { it.name },
        )
    }
}

object RuleConfidenceSerializer :
    EnumNameSerializer<RuleConfidence>("org.panchang.wire.RuleConfidence", RuleConfidence.entries.toTypedArray())

object VerificationStatusSerializer :
    EnumNameSerializer<VerificationStatus>("org.panchang.wire.VerificationStatus", VerificationStatus.entries.toTypedArray())

object ObservanceAnchorSerializer :
    EnumNameSerializer<ObservanceAnchor>("org.panchang.wire.ObservanceAnchor", ObservanceAnchor.entries.toTypedArray())

object AbsenceReasonSerializer :
    EnumNameSerializer<AbsenceReason>("org.panchang.wire.AbsenceReason", AbsenceReason.entries.toTypedArray())

object EventGroupSerializer :
    EnumNameSerializer<EventGroup>("org.panchang.wire.EventGroup", EventGroup.entries.toTypedArray())

object FastKindSerializer :
    EnumNameSerializer<FastKind>("org.panchang.wire.FastKind", FastKind.entries.toTypedArray())

object MahadvadashiTypeSerializer :
    EnumNameSerializer<MahadvadashiType>("org.panchang.wire.MahadvadashiType", MahadvadashiType.entries.toTypedArray())

object ParanaBoundReasonSerializer :
    EnumNameSerializer<ParanaBoundReason>("org.panchang.wire.ParanaBoundReason", ParanaBoundReason.entries.toTypedArray())

object PakshaSerializer :
    EnumNameSerializer<Paksha>("org.panchang.wire.Paksha", Paksha.entries.toTypedArray())

/**
 * ISO-8601 `yyyy-MM-dd`, which is what `LocalDate.toString` and `LocalDate.parse` already agree
 * on. Explicitly *not* a locale-sensitive `DateTimeFormatter`: the same build must produce the
 * same bytes on a developer's machine and on a build agent in another country.
 */
object LocalDateSerializer : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.panchang.wire.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
