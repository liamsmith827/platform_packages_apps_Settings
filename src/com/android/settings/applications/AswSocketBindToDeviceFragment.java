package com.android.settings.applications;

import android.content.Context;
import android.ext.settings.app.AppSwitch;

import androidx.appcompat.app.AlertDialog;

import com.android.settings.R;

public abstract class AswSocketBindToDeviceFragment<T extends AppSwitch>
        extends AswProtectionWarnOnDisableFragment<T> {

    public AlertDialog.Builder getWarningOnDisable(Context ctx, Runnable action) {
        var b = new AlertDialog.Builder(ctx);
        b.setTitle(R.string.ap_confirm_disable_title);
        b.setMessage(R.string.ap_socket_bindtodevice_confirm_disable_warning_msg);
        b.setNegativeButton(R.string.cancel, null);
        b.setPositiveButton(R.string.ap_confirm_disable_proceed_btn, (d, w) -> action.run());
        return b;
    }
}
