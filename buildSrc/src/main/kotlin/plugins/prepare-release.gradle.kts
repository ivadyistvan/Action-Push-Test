import org.ajoberstar.grgit.Commit
import org.ajoberstar.grgit.Grgit

plugins {
    id("org.ajoberstar.grgit")
}

tasks.register("prepareRelease") {
    group = "Release"
    description = "Prepares a new release from the main branch."

    doLast {
        fetchLatestData()
        val newVersion = getNewVersionName()
        updateReleaseNotes(newVersion)
        val releaseCommit = commitReleaseNotes(newVersion)
        createReleaseTag(releaseCommit, newVersion)
        val releaseBranch = createReleaseBranch(newVersion)
        pushRelease(releaseBranch)
        println("\n🎉 Successfully prepared release $newVersion!")
    }
}

private fun fetchLatestData() {
    println("▶️  Fetching latest data from origin...")
    grgit.fetch()
}

private fun getNewVersionName(): String {
    val versionNameInput: String? = project.findProperty("versionName") as? String

    val newVersion = if (versionNameInput.isNullOrBlank()) {
        println("🟡  Version name not provided, determining from the latest git tag...")
        determineNewVersionName()
    } else {
        versionNameInput
    }
    println("✅  New version: $newVersion")
    return newVersion
}

private fun determineNewVersionName(): String {
    val latestVersionTag = grgit.tag.list()
        .filter { versionRegex.matches(it.name) }
        .maxByOrNull { it.commit.dateTime }
        ?.name
    val latestVersionName = if (latestVersionTag.isNullOrBlank()) {
        println("🟡  No version tag found, going with $baseVersionName...")
        baseVersionName
    } else {
        println("✅  Latest tag found: $latestVersionTag...")
        latestVersionTag
    }
    return increaseVersionName(latestVersionName)
}

private fun increaseVersionName(lastVersionName: String): String {
    val match = versionRegex.find(lastVersionName)
        ?: throw IllegalArgumentException("❌  Tag '$lastVersionName' does not match the 'vX.Y' format.")
    val (major, minor) = match.destructured
    return "v$major.${minor.toInt() + 1}"
}

private fun updateReleaseNotes(newVersion: String) {
    grgit.checkout { branch = baseBranch }

    val releaseNotes = rootProject.file(releaseNotesPath)
    releaseNotes.parentFile.mkdirs()

    val releaseNotesUrlInput: String? = project.findProperty("releaseNotesUrl") as? String

    val releaseNotesContent = buildString {
        append(String.format(releaseNotesVersionTemplate, newVersion))
        if (releaseNotesUrlInput.isNullOrBlank()) {
            println("🟡  No release notes URL provided...")
        } else {
            appendLine()
            append(releaseNotesUrlInput)
        }
    }
    println("▶️  Updating release notes at: ${releaseNotes.path}")
    releaseNotes.writeText(releaseNotesContent)
}

private fun commitReleaseNotes(newVersion: String): Commit {
    val commitMessage = String.format(releaseNotesCommitMessage, newVersion)
    println("▶️  Adding and committing changes: \"$commitMessage\"")
    grgit.add { patterns = setOf(releaseNotesPath) }
    return grgit.commit { message = commitMessage }
}

private fun createReleaseTag(releaseCommit: Commit, newVersion: String) {
    println("▶️  Creating git tag: $newVersion")
    grgit.tag.add {
        name = newVersion
        message = String.format(releaseNotesCommitMessage, newVersion)
        pointsTo = releaseCommit
    }
}

private fun createReleaseBranch(newVersion: String): String {
    val releaseBranchName = String.format(releaseBranchTemplate, newVersion)
    println("▶️  Creating new branch '$releaseBranchName' from '$newVersion' tag...")
    grgit.checkout {
        branch = releaseBranchName
        createBranch = true
        startPoint = newVersion
    }
    return releaseBranchName
}

private fun pushRelease(newBranch: String) {
    println("▶️  Pushing release branch and tag to 'origin'...")
    grgit.push {
        refsOrSpecs = listOf(baseBranch, newBranch)
        tags = true
    }
}

val grgit: Grgit = Grgit.open(mapOf("currentDir" to project.rootDir))

private val versionRegex = "v(\\d+)\\.(\\d+)".toRegex()

private val baseVersionName = "v0.0"

private val releaseBranchTemplate = "release/%s"

private val baseBranch = "main"

private val releaseNotesPath = "tools/release/release_notes.txt"

private val releaseNotesVersionTemplate = "Release %s"

private val releaseNotesCommitMessage = "Release %s"
