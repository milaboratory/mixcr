#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const scriptDir = __dirname;
const packageRoot = path.resolve(scriptDir, "../");
const dstRoot = path.join(packageRoot, "dld");
const dstDataDir = path.join(dstRoot, "mixcr");

function log(message) {
    console.log(message);
}

if (!process.env.CI) {
    process.exit(0);
}

const ARCHIVE_PATH = process.env.ARCHIVE_PATH;
if (!ARCHIVE_PATH) {
    log(
        "To unpack existing archive after installation, provide 'ARCHIVE_PATH'" +
        "environment variable with path to the mixcr archive before running 'npm ci' or 'npm install'"
    );
    process.exit(0)
}

function isDir(target) {
    const stats = fs.statSync(target);
    return stats.isDirectory();
}

function findZipArchive(archivePath) {
    if (!isDir(archivePath)) {
        return archivePath
    }

    const files = fs.readdirSync(archivePath, { recursive: false })
    const zipFiles = files.filter((name) => name.endsWith(".zip"))

    if (zipFiles.length === 0) {
        log(`No zip archive found in '${archivePath}'`);
        return "";
    } else if (zipFiles.length > 1) {
        log("More than one zip archive found. Please specify which one to use.");
        return "";
    } else {
        return path.join(archivePath, zipFiles[0]);
    }
}

const archivePath = findZipArchive(ARCHIVE_PATH);
if (!archivePath) {
    process.exit(1);
}

log(`Zip archive found at '${archivePath}'`);

if (fs.existsSync(dstDataDir)) {
    log(`Removing old data dir: ${dstDataDir}`);
    fs.rmSync(dstDataDir, { recursive: true, force: true });
}
log(`Creating clean data dir: ${dstDataDir}`);
fs.mkdirSync(dstDataDir, { recursive: true });

const unzipper = require("unzipper");

fs.createReadStream(archivePath)
    .pipe(unzipper.Extract({ path: dstDataDir }))
    .on("close", () => log(`Extraction complete to '${dstDataDir}'`));
