package com.android.settings.applications;

import android.content.Context;
import android.content.pm.GosPackageState;
import android.ext.settings.app.AppSwitch;

import androidx.appcompat.app.AlertDialog;

import com.android.settings.R;

// TODO: Make category title Privacy protection settings.

/**
 * Displays a warning dialog when the user disables a protection. Note that disabling a protection
 * can appear as an enabling action in the user interface. For example, the user enabling WebView
 * JIT is disabling the protection.
 */
public abstract class AswProtectionWarnOnDisableFragment<T extends AppSwitch> extends
        AswAppInfoFragment<T> {

    @Override
    protected void completeStateChange(int newEntryId, boolean curValue, Runnable stateChangeAction) {
        Context ctx = requireContext();

        boolean showWarning = false;
        if (curValue) {
            if (newEntryId == ID_OFF) {
                showWarning = true;
            } else if (newEntryId == ID_DEFAULT) {
                AppSwitch asw = adapter.getAppSwitch();
                int userId = mUserId;
                var ps = GosPackageState.get(mPackageName, userId);
                showWarning = !asw.getDefaultValue(ctx, userId, getAppInfo(), ps);
            }
        }
        if (showWarning) {
            var d = getWarningOnDisable(ctx, stateChangeAction);
            d.show();
        } else {
            stateChangeAction.run();
        }
    }

    public abstract AlertDialog.Builder getWarningOnDisable(Context ctx, Runnable action);
}
