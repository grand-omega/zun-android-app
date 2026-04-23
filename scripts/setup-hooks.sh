#!/bin/sh

# This script installs a git pre-commit hook to automatically run spotlessApply.
# It ensures your code is always formatted correctly before it's committed.

HOOK_PATH=".git/hooks/pre-commit"

echo "#!/bin/sh" > $HOOK_PATH
echo "" >> $HOOK_PATH
echo "echo 'Running Spotless formatting...'" >> $HOOK_PATH
echo "./gradlew spotlessApply" >> $HOOK_PATH
echo "git add ." >> $HOOK_PATH

chmod +x $HOOK_PATH

echo "✅ Git pre-commit hook installed successfully!"
