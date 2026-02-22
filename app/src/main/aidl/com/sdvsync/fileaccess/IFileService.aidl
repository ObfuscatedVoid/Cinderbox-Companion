package com.sdvsync.fileaccess;

interface IFileService {
    void destroy() = 16777114;
    boolean fileExists(String path) = 1;
    String[] listDirectory(String path) = 2;
    byte[] readFileChunk(String path, long offset, int length) = 3;
    long getFileSize(String path) = 4;
    void writeFileChunk(String path, in byte[] data, long offset) = 5;
    void deleteFile(String path) = 6;
    void createDirectory(String path) = 7;
    boolean renameFile(String fromPath, String toPath) = 8;
}
