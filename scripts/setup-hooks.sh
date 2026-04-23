#!/bin/sh

# This script installs a git pre-commit hook to automatically run spotlessApply.
# It ensures your code is always formatted correctly before it's committed.

HOOK_PATH=".git/hooks/pre-commit"

echo "#!/bin/sh" > $HOOK_PATH
echo "" >> $HOOK_PATH
echo "# Only run if java is available" >> $HOOK_PATH
echo "if command -v java >/dev/null 2>&1 || [ -n \"\$JAVA_HOME\" ]; then" >> $HOOK_PATH
echo "    echo 'Running Spotless formatting...'" >> $HOOK_PATH
echo "    ./gradlew spotlessApply" >> $HOOK_PATH
echo "    git add ." >> $HOOK_PATH
echo "else" >> $HOOK_PATH
echo "    echo '⚠️  Warning: Java not found. Skipping automatic Spotless formatting.'" >> $HOOK_PATH
echo "fi" >> $HOOK_PATH

chmod +x $HOOK_PATH

echo "✅ Git pre-commit hook installed successfully!"
