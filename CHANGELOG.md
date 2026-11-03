# Changelog

## Unreleased — beta preparation

- Show recursive search results progressively, with a stop button and notices for incomplete searches.
- Restore the previous folder listing when search is cleared, and prevent an older search from replacing a newer result or showing a stale connection error.
- Save single and multiple downloads to a folder chosen through Android's folder picker.
- Keep existing destination files and give same-named downloads numbered filenames. Attempt to remove incomplete files when a transfer fails.
- Show file extension badges beside long filenames and add file/folder information to the three-dot menu.
- Show selected-item checkboxes, exact paths in delete confirmations, and separate success/failure counts for batch deletion. Nonempty folders are retained.
- Treat wildcard characters in selected SFTP paths literally for browsing, download, and deletion.

Downloads do not resume automatically after the app process is killed. Folder downloads are not supported. See [beta limitations](README.md#beta-limitations) and [device checks](docs/files-beta-testing.md).
