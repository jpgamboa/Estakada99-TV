# Setup Notes

## 1. Replace ExoPlayer2 with Media3 in build.gradle (app)

Remove old dependency:
    implementation 'com.google.android.exoplayer:exoplayer:2.x.x'

Add Media3:
    implementation 'androidx.media3:media3-exoplayer:1.3.1'
    implementation 'androidx.media3:media3-ui:1.3.1'
    implementation 'androidx.media3:media3-exoplayer-hls:1.3.1'   // if stream is HLS (.m3u8)

## 2. AndroidManifest.xml — make sure these are present

    <uses-permission android:name="android.permission.INTERNET" />

    Inside <activity>:
        android:screenOrientation="landscape"
        android:configChanges="keyboard|keyboardHidden|orientation|screenSize"

    For Android TV leanback:
        <uses-feature android:name="android.software.leanback" android:required="false" />
        <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

## 3. What was fixed

- PlayerView was 1dp x 1dp (invisible!) → now fullscreen
- Logo/background now hidden once stream starts playing
- Added buffering and error states with user-visible messages
- Added TV remote control: OK to retry, Back to exit
- Added onPause/onResume lifecycle (player pauses when app is backgrounded)
- Migrated from deprecated ExoPlayer2 → Media3
- Added fullscreen immersive mode flags
