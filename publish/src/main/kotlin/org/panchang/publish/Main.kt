package org.panchang.publish

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates the published feed for a list of sites into a local directory.
 *
 * ```
 * ./gradlew :publish:run --args="--sites publish/sites/example.tsv --year 2026 --out build/feed"
 * ```
 *
 * There is no deploy step here and no credential of any kind. This command reads a text file and
 * writes files; moving them anywhere is a separate concern with a separate review.
 *
 * Conventions — UTF-8 pinned streams, refusals as sentences on stderr, [ProgramResult] for the
 * exit status — follow `:calc`'s command, which established them for this repository.
 */
class PublishCommand(
    private val out: Appendable = System.out,
    private val err: Appendable = System.err,
    private val publisher: FeedPublisher = FeedPublisher(),
) : CliktCommand(name = "panchang-publish") {

    override fun help(context: Context) =
        "Generate the static calendar feed for a list of sites into a local directory. " +
            "Emits the v1 payloads (:wire's document roots), the legacy feed the shipped " +
            "Android app already parses, and a manifest and skip list that account for every " +
            "requested site."

    private val sites by option(
        "--sites",
        help = "Site list file. One site per line: 'coords <key> <lat> <lon> <tz> <title>' or " +
            "'place-id <key> <geonameId> [title]'. Blank lines and # comments ignored.",
    ).required()

    private val year by option("--year", help = "Calendar year to publish.").int().required()

    private val sampradaya by option(
        "--sampradaya",
        help = "Tradition id. Default: iskcon.",
    ).default("iskcon")

    private val out0 by option(
        "--out",
        help = "Output directory. Created if absent; existing files of the same names are " +
            "overwritten, and nothing else in it is touched.",
    ).required()

    override fun run() {
        val sitesPath = Path.of(sites)
        if (!Files.isRegularFile(sitesPath)) {
            fail("No site list at '$sites'. Pass --sites pointing at a readable file.")
        }
        val specs = SiteList.parse(Files.readString(sitesPath, StandardCharsets.UTF_8))
        if (specs.isEmpty()) {
            fail(
                "The site list at '$sites' has no entries. Publishing an empty feed would " +
                    "replace a working one with nothing, so it is refused rather than done " +
                    "quietly.",
            )
        }

        val result = try {
            publisher.run(specs, year, sampradaya)
        } catch (e: IllegalArgumentException) {
            fail(e.message ?: "The publish run was refused with no message, which is itself a bug.")
        } catch (e: IllegalStateException) {
            fail(e.message ?: "The publish run failed a self-check with no message.")
        }

        publisher.write(result, Path.of(out0))

        val m = result.manifest
        out.append(
            "requested ${m.requested}, published ${m.published}, skipped ${m.skipped}; " +
                "legacy published ${m.legacyPublished}, withheld ${m.legacyWithheld}; " +
                "${result.files.size} files under $out0\n",
        )

        // Everything that is not the summary goes to stderr, so it survives a redirect of stdout
        // and so an operator scrolling past it has to scroll past it.
        result.skipped.sites.forEach {
            err.append(
                "[publish] SKIPPED ${it.key} (${it.siteRejectionCode ?: it.inputRejectionCode}): " +
                    "${it.reason}\n",
            )
        }
        m.legacyWithheldSites.forEach {
            err.append("[publish] NO LEGACY FILE ${it.key} (${it.code}): ${it.timeZone}\n")
        }
        m.legacyParanaOmissions.forEach {
            err.append(
                "[publish] PARANA NOT IN LEGACY FEED ${it.key} ${it.fastDate} " +
                    "${it.observance}: ${it.reason}\n",
            )
        }
        m.warnings.forEach { err.append("[publish] WARNING $it\n") }
    }

    private fun fail(message: String): Nothing {
        err.append(message).append('\n')
        throw ProgramResult(1)
    }
}

/**
 * Entry point, with the streams pinned to UTF-8 for the same reason `:calc`'s is: on Windows the
 * JVM gives `System.out` the console code page, and the event names this project prints carry
 * characters that page cannot represent.
 */
fun main(args: Array<String>) {
    val stdout = PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8")
    val stderr = PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8")
    try {
        PublishCommand(out = stdout, err = stderr).main(args)
    } finally {
        stdout.flush()
        stderr.flush()
    }
}
