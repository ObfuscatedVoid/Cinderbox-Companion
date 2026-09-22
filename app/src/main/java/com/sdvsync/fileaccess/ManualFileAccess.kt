package com.sdvsync.fileaccess

class ManualFileAccess : FileAccessStrategy by AllFilesAccess() {
    override val name = "Manual"
}
