# My Camera - GitHub APK build

1. Create a new GitHub repository (private is fine).
2. Upload all project files and folders at the repository root, including the hidden `.github/workflows/build-apk.yml` file.
3. Open Actions > Build My Camera APK > Run workflow (or push to main).
4. Wait for the green check, open the run, download `MyCamera-APK` from Artifacts.
5. Unzip that artifact on your Android phone, then install `app-debug.apk`.

The app uses the camera only with Android camera permission. The build has not been run/tested here.
