#!/system/bin/sh
# M3H Gesture Navigation — uninstall cleanup

PACKAGE="com.m3h.gesturenav"
ACCESSIBILITY_SVC="$PACKAGE/com.m3h.gesturenav.GestureAccessibilityService"

# Remove from accessibility services list
CURRENT=$(settings get secure enabled_accessibility_services 2>/dev/null)
if [ -n "$CURRENT" ] && [ "$CURRENT" != "null" ]; then
    # Remove our entry and clean up separators
    NEW=$(echo "$CURRENT" \
        | sed "s|${ACCESSIBILITY_SVC}||g" \
        | sed 's/^://;s/:$//;s/::/:/g')
    if [ -z "$NEW" ] || [ "$NEW" = "null" ]; then
        settings delete secure enabled_accessibility_services 2>/dev/null
    else
        settings put secure enabled_accessibility_services "$NEW" 2>/dev/null
    fi
fi

# Clear app data
pm clear "$PACKAGE" 2>/dev/null
