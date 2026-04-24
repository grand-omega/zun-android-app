#!/bin/sh

# Installs git hooks:
#   pre-commit: runs spotlessApply so formatting is consistent
#   pre-push:   runs lintDebug so CI-breaking lint errors are caught locally

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

PREPUSH_PATH=".git/hooks/pre-push"

echo "#!/bin/sh" > $PREPUSH_PATH
echo "" >> $PREPUSH_PATH
echo "# Only run if java is available" >> $PREPUSH_PATH
echo "if command -v java >/dev/null 2>&1 || [ -n \"\$JAVA_HOME\" ]; then" >> $PREPUSH_PATH
echo "    echo 'Running lintDebug before push...'" >> $PREPUSH_PATH
echo "    if ! ./gradlew :app:lintDebug --quiet; then" >> $PREPUSH_PATH
echo "        echo '❌ Lint errors found. Fix them or bypass with: git push --no-verify'" >> $PREPUSH_PATH
echo "        exit 1" >> $PREPUSH_PATH
echo "    fi" >> $PREPUSH_PATH
echo "else" >> $PREPUSH_PATH
echo "    echo '⚠️  Warning: Java not found. Skipping pre-push lint check.'" >> $PREPUSH_PATH
echo "fi" >> $PREPUSH_PATH

chmod +x $PREPUSH_PATH

echo "✅ Git pre-push hook installed successfully!"
