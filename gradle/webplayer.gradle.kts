// ── fetchWebPlayer ────────────────────────────────────────────────────────────
// Applies to the ROOT project. Every build of :app / :desktop runs this task,
// which fetches the LATEST web player release from the musicclient repository
// and refreshes the two bundled music.zip files:
//
//   desktop/src/desktopMain/resources/music.zip   (desktop JAR resource)
//   app/src/main/res/raw/music.zip                (Android raw resource)
//
// The release asset is a full source archive; only the files the servers
// actually serve (index.html, js/app.js, plus LICENSE.txt when present) are
// extracted into a minimal, deterministic music.zip.
//
// Options:
//   -PwebPlayerVersion=<tag>   pin a specific release tag (default: latest)
//   -PgithubToken=<token>      GitHub API token (or GITHUB_TOKEN env var),
//                              avoids the 60 req/h unauthenticated rate limit

import groovy.json.JsonSlurper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

val webPlayerOwner = "wwwjsw"
val webPlayerRepo = "musicclient"
val webPlayerRequiredEntries = listOf("index.html", "js/app.js")
val webPlayerOptionalEntries = listOf("LICENSE.txt")
val webPlayerTargets = listOf(
    rootProject.file("desktop/src/desktopMain/resources/music.zip"),
    rootProject.file("app/src/main/res/raw/music.zip"),
)

fun sha256Hex(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

tasks.register("fetchWebPlayer") {
    group = "build"
    description = "Fetches the latest web player release from wwwjsw/musicclient and updates the bundled music.zip files."

    // Always re-check the releases API so a newly published player is picked up
    // by the very next build.
    outputs.upToDateWhen { false }

    doLast {
        val tagOverride = providers.gradleProperty("webPlayerVersion").orNull
        val token = providers.gradleProperty("githubToken").orNull
            ?: System.getenv("GITHUB_TOKEN")

        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        fun httpGet(url: String): String {
            val builder = HttpRequest.newBuilder(URI.create(url)).GET()
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                throw GradleException("fetchWebPlayer: HTTP ${response.statusCode()} for $url")
            }
            return response.body()
        }

        val apiUrl = if (tagOverride != null) {
            "https://api.github.com/repos/$webPlayerOwner/$webPlayerRepo/releases/tags/$tagOverride"
        } else {
            "https://api.github.com/repos/$webPlayerOwner/$webPlayerRepo/releases/latest"
        }

        // Resolve release metadata. When tracking "latest" and the API is
        // unreachable, keep the already bundled player (offline builds keep
        // working). An explicit -PwebPlayerVersion= pin must never silently
        // fall back to an older player, so it fails loudly.
        val releaseJson: Map<*, *> = try {
            JsonSlurper().parseText(httpGet(apiUrl)) as Map<*, *>
        } catch (e: Exception) {
            if (tagOverride == null && webPlayerTargets.all { it.isFile }) {
                logger.warn(
                    "fetchWebPlayer: could not reach the GitHub API (${e.message}); " +
                        "keeping the already bundled web player."
                )
                return@doLast
            }
            throw GradleException(
                if (tagOverride != null) {
                    "fetchWebPlayer: release '$tagOverride' could not be resolved ($apiUrl): " +
                        "${e.message} — the requested version may not exist."
                } else {
                    "fetchWebPlayer: failed to resolve the web player release ($apiUrl): ${e.message} " +
                        "— check connectivity or pass -PgithubToken=<token>."
                },
                e,
            )
        }

        val tagName = releaseJson["tag_name"] as? String
            ?: throw GradleException("fetchWebPlayer: release response has no tag_name: $releaseJson")
        val assets = releaseJson["assets"] as? List<*>
            ?: throw GradleException("fetchWebPlayer: release response has no assets: $releaseJson")
        val asset = assets.mapNotNull { it as? Map<*, *> }
            .firstOrNull { (it["name"] as? String)?.endsWith(".zip") == true }
            ?: throw GradleException(
                "fetchWebPlayer: no .zip asset in release $tagName — found: " +
                    assets.mapNotNull { (it as? Map<*, *>)?.get("name") }
            )

        val assetName = asset["name"] as String
        val downloadUrl = asset["browser_download_url"] as? String
            ?: throw GradleException("fetchWebPlayer: asset $assetName has no browser_download_url")
        val expectedDigest = (asset["digest"] as? String)
            ?.removePrefix("sha256:")
            ?.lowercase()

        // ── Download + verify (cached per tag in the root build dir) ──────────
        val cacheDir = rootProject.layout.buildDirectory.dir("webplayer-cache").get().asFile
        val tagCacheDir = File(cacheDir, tagName)
        val archiveFile = File(tagCacheDir, assetName)

        if (!archiveFile.isFile || (expectedDigest != null && sha256Hex(archiveFile) != expectedDigest)) {
            logger.lifecycle("fetchWebPlayer: downloading web player $tagName ($assetName) …")
            tagCacheDir.mkdirs()
            val response = client.send(
                HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofFile(archiveFile.toPath()),
            )
            if (response.statusCode() != 200) {
                throw GradleException(
                    "fetchWebPlayer: failed to download $downloadUrl (HTTP ${response.statusCode()})"
                )
            }
            if (expectedDigest != null) {
                val actual = sha256Hex(archiveFile)
                if (actual != expectedDigest) {
                    archiveFile.delete()
                    throw GradleException(
                        "fetchWebPlayer: sha256 mismatch for $assetName — " +
                            "expected $expectedDigest, got $actual"
                    )
                }
            }
        }

        // ── Build the minimal, deterministic music.zip ────────────────────────
        val missing = webPlayerRequiredEntries.filter { entry ->
            ZipFile(archiveFile).use { zip -> zip.getEntry(entry) == null }
        }
        if (missing.isNotEmpty()) {
            val found = ZipFile(archiveFile).use { zip ->
                zip.entries().asSequence().map { it.name }.sorted().toList()
            }
            throw GradleException(
                "fetchWebPlayer: release $tagName is missing ${missing.joinToString()} — " +
                    "upstream player structure changed. Entries found: $found"
            )
        }

        val tmpZip = File.createTempFile("music", ".zip")
        try {
            ZipFile(archiveFile).use { zip ->
                val names = webPlayerRequiredEntries +
                    webPlayerOptionalEntries.filter { zip.getEntry(it) != null }
                ZipOutputStream(tmpZip.outputStream().buffered()).use { out ->
                    for (name in names) {
                        val entry = ZipEntry(name)
                        entry.time = 0L // fixed timestamp → byte-identical output
                        out.putNextEntry(entry)
                        zip.getInputStream(zip.getEntry(name)).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
            }
            val newBytes = tmpZip.readBytes()

            // ── Install into both modules (only when content changed) ──────────
            var changed = false
            for (target in webPlayerTargets) {
                val oldBytes = if (target.isFile) target.readBytes() else null
                if (oldBytes?.contentEquals(newBytes) != true) {
                    target.parentFile.mkdirs()
                    target.writeBytes(newBytes)
                    changed = true
                    logger.lifecycle(
                        "fetchWebPlayer: updated ${rootProject.relativePath(target)} ($tagName, ${newBytes.size} bytes)"
                    )
                }
            }

            val marker = rootProject.layout.buildDirectory.file("webplayer/latest-tag.txt").get().asFile
            marker.parentFile.mkdirs()
            marker.writeText(tagName)

            if (!changed) {
                logger.lifecycle("fetchWebPlayer: web player already up to date ($tagName)")
            }
        } finally {
            tmpZip.delete()
        }
    }
}
