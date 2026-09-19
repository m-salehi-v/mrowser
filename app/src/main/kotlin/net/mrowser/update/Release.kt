package net.mrowser.update

/**
 * One GitHub release, as much of it as the update channel needs.
 *
 * [tag] is the git tag as published ("v1.4.0"); [version] is the same with any leading `v`
 * stripped, which is the form that compares against `PackageManager`'s `versionName`.
 */
data class Release(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)
