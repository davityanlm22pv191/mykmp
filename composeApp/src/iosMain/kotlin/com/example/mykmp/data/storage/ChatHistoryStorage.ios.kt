package com.example.mykmp.data.storage

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.writeToFile

private fun getStorageFilePath(key: String): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    val documentsDir = paths.first() as String
    return (documentsDir as NSString).stringByAppendingPathComponent("$key.json")
}

actual fun saveToStorage(key: String, value: String) {
    val path = getStorageFilePath(key)
    (value as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
}

actual fun loadFromStorage(key: String): String? {
    val path = getStorageFilePath(key)
    return if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
        NSString.create(contentsOfFile = path, encoding = NSUTF8StringEncoding, error = null) as? String
    } else {
        null
    }
}
