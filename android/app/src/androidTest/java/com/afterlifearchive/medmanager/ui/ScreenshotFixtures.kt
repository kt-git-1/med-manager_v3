package com.afterlifearchive.medmanager.ui

import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.platform.app.InstrumentationRegistry

internal fun writeDeviceScreenshotFixture(name: String) {
    val bitmap = checkNotNull(
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
    ) { "Could not capture device display: $name" }
    writeScreenshotFixture(bitmap.asImageBitmap(), name)
}

internal fun writeScreenshotFixture(image: ImageBitmap, name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val persist = InstrumentationRegistry.getArguments()
        .getString("persistScreenshotFixtures")
        .toBoolean()
    if (persist && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/med-manager-screenshot-fixtures",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        ) { "Could not create screenshot fixture: $name" }
        resolver.openOutputStream(uri).use { stream ->
            checkNotNull(stream)
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return
    }

    val directory = java.io.File(
        context.externalCacheDir ?: context.cacheDir,
        "screenshot-fixtures",
    ).apply { mkdirs() }
    val file = java.io.File(directory, name)
    file.outputStream().use { stream ->
        image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    check(file.length() > 0) { "Screenshot fixture was not written: $name" }
}
