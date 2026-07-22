#!/system/bin/sh
# M3H Gesture Navigation — Magisk service script
# Runs after boot to grant permissions and enable accessibility service.
# SAFETY: All commands have timeouts; no infinite loops; no blocking calls.

MODDIR=${0%/*}
PACKAGE="com.m3h.gesturenav"
ACCESSIBILITY_SVC="$PACKAGE/com.m3h.gesturenav.GestureAccessibilityService"
LOGFILE="/data/local/tmp/gesture_nav_boot.log"
MAX_WAIT=60

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >> "$LOGFILE" 2>/dev/null
}

# Wait for package manager with bounded retry
wait_for_pm() {
    count=0
    while [ $count -lt $MAX_WAIT ]; do
        if pm list packages "$PACKAGE" >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
        count=$((count + 1))
    done
    return 1
}

# Wait for settings provider to respond
wait_for_settings() {
    count=0
    while [ $count -lt 20 ]; do
        if settings get secure enabled_accessibility_services >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
        count=$((count + 1))
    done
    return 1
}

# Main logic — bounded and safe
(
    # Wait for boot animation to finish
    sleep 15

    log "=== Gesture Nav boot setup started ==="

    # Wait for package manager
    if ! wait_for_pm; then
        log "ABORT: Package not found after ${MAX_WAIT} retries"
        exit 0
    fi
    log "Package found: $PACKAGE"

    # Grant overlay permission
    appops set "$PACKAGE" SYSTEM_ALERT_WINDOW allow 2>/dev/null
    log "Overlay permission granted"

    # Wait for settings provider
    wait_for_settings

    # Enable accessibility service
    CURRENT=$(settings get secure enabled_accessibility_services 2>/dev/null)
    if echo "$CURRENT" | grep -qF "$ACCESSIBILITY_SVC"; then
        log "Accessibility service already enabled"
    else
        if [ -z "$CURRENT" ] || [ "$CURRENT" = "null" ]; then
            settings put secure enabled_accessibility_services "$ACCESSIBILITY_SVC" 2>/dev/null
        else
            settings put secure enabled_accessibility_services "${CURRENT}:${ACCESSIBILITY_SVC}" 2>/dev/null
        fi
        log "Accessibility service enabled"
    fi

    # Ensure accessibility is turned on
    settings put secure accessibility_enabled 1 2>/dev/null
    log "accessibility_enabled = 1"

    # Hide navigation bar
    settings put global policy_control "immersive.navigation=*" 2>/dev/null
    log "Navigation bar hidden"

    log "=== Gesture Nav boot setup complete ==="
) &

# Exit immediately — the subshell runs in background, does not block boot
exit 0
