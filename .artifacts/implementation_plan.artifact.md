# Plan to upload the new version to GitHub

The goal is to upload the current Android project to GitHub. Currently, the project folder does not seem to be a Git repository, or it is incorrectly picking up a different repository nearby.

## User Review Required

> [!IMPORTANT]
> I need to confirm which GitHub repository you want to use. I found an existing repository at `https://github.com/DucBao98112/hotline-tv-test.git`, but it currently contains different files (HTML/JS). Do you want to:
> 1. Overwrite that repository with this Android project?
> 2. Push to a new branch in that repository?
> 3. Push to a completely different repository?

## Proposed Changes

### [Git Configuration]

#### [NEW] Initialize Git
- Run `git init` in the project root: `/Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2`
- Ensure `.gitignore` is correctly configured (it exists but I will verify it covers all Android build files).

#### [MODIFY] Version Increment (Optional but recommended for "new version")
- Update `versionCode` and `versionName` in `app/build.gradle`.
- Current: `versionCode 16`, `versionName "1.0.7.4"`
- Proposed: `versionCode 17`, `versionName "1.0.7.5"` (or as requested).

#### [EXECUTE] Commit and Push
- `git add .`
- `git commit -m "Upload new version 1.0.7.4"`
- `git remote add origin <URL>`
- `git push -u origin main` (or the appropriate branch).

## Verification Plan

### Manual Verification
- Check the GitHub repository URL to ensure the files are successfully uploaded.
- Verify that the commit history reflects the new version.
