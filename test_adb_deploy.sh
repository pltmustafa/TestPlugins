#!/bin/bash
PROJECT=$1
if [ -z "$PROJECT" ]; then
    echo "Usage: ./test_adb_deploy.sh ProjectName"
    exit 1
fi
echo "Building $PROJECT..."
./gradlew $PROJECT:make
if [ $? -eq 0 ]; then
    echo "Build successful. Deploying..."
    adb push $PROJECT/build/$PROJECT.cs3 /sdcard/Download/$PROJECT.cs3
    adb shell am start -d "file:///sdcard/Download/$PROJECT.cs3" -a android.intent.action.VIEW -t "application/octet-stream" -n com.lagradost.cloudstream3/.MainActivity
else
    echo "Build failed!"
fi
