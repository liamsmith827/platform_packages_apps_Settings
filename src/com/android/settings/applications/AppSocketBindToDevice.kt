package com.android.settings.applications

import android.content.Context
import android.content.pm.ApplicationInfo
import android.ext.settings.app.AppSwitch
import android.ext.settings.app.AswSocketBindToDevice
import android.ext.settings.app.AswUseHardenedMalloc
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.android.settings.R
import com.android.settings.spa.app.appinfo.AswPreference
import com.android.settingslib.widget.FooterPreference

object AswAdapterSocketBindToDevice : AswAdapter<AswSocketBindToDevice>() {

    override fun getAppSwitch() = AswSocketBindToDevice.I

    override fun getAswTitle(ctx: Context) = ctx.getText(R.string.ap_socket_bindtodevice)
    override fun getOnTitle(ctx: Context) = ctx.getText(R.string.ap_disabled)
    override fun getOffTitle(ctx: Context) = ctx.getText(R.string.ap_enabled)

    override fun getDetailFragmentClass() = AppSocketBindToDeviceFragment::class
}

@Composable
fun AppSocketBindToDevicePreference(app: ApplicationInfo) {
    val context = LocalContext.current
    AswPreference(context, app, AswAdapterSocketBindToDevice)
}

class AppSocketBindToDeviceFragment : AswSocketBindToDeviceFragment<AswSocketBindToDevice>() {

    override fun getAswAdapter() = AswAdapterSocketBindToDevice

    override fun getSummaryForImmutabilityReason(ir: Int): CharSequence? {
        val id = when (ir) {
            // TODO: Force enable this because system apps can bypass VPN anyway.
            AppSwitch.IR_IS_SYSTEM_APP -> R.string.ap_socket_bindtodevice_ir_preinstalled_app
            else -> return null
        }
        return getText(id)
    }

    override fun updateFooter(fp: FooterPreference) {
        fp.setTitle(R.string.ap_socket_bindtodevice_footer)
        // TODO: Remove this.
        setLearnMoreLink(fp, "https://grapheneos.org/features#exploit-mitigations")
    }
}


class SocketBindToDeviceAppListPrefController(context: Context, preferenceKey: String) :
    AswAppListPrefController(context, preferenceKey, AswAdapterSocketBindToDevice) {

    override fun getAvailabilityStatus() = AVAILABLE
}
