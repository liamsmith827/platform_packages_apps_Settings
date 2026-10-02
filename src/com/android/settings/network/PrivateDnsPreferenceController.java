/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.network;

import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
import static android.provider.Settings.Global.PRIVATE_DNS_DEFAULT_MODE;
import static android.provider.Settings.Global.PRIVATE_DNS_MODE;
import static android.provider.Settings.Global.PRIVATE_DNS_SPECIFIER;
import static android.provider.Settings.Secure.VPN_PRIVATE_DNS_DEFAULT_MODE;
import static android.provider.Settings.Secure.VPN_PRIVATE_DNS_MODE;
import static android.provider.Settings.Secure.VPN_PRIVATE_DNS_SPECIFIER;

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.ext.ConnectivityUtil.NetworkType;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.ConnectivitySettingsManager;
import android.net.GlobalOrUserId;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.util.ArrayUtils;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.net.InetAddress;
import java.util.List;

public class PrivateDnsPreferenceController extends BasePreferenceController
        implements PreferenceControllerMixin, LifecycleObserver, OnStart, OnStop {

    public static String getKey(NetworkType networkType) {
        if (networkType == NetworkType.PHYSICAL) {
            return "physical_private_dns_settings";
        }
        return "vpn_private_dns_settings";
    }

    private static Uri[] getSettingsUris(NetworkType networkType) {
        if (networkType == NetworkType.PHYSICAL) {
            return new Uri[]{
                    Settings.Global.getUriFor(PRIVATE_DNS_MODE),
                    Settings.Global.getUriFor(PRIVATE_DNS_DEFAULT_MODE),
                    Settings.Global.getUriFor(PRIVATE_DNS_SPECIFIER),
            };
        } else {
            return new Uri[]{
                    Settings.Secure.getUriFor(VPN_PRIVATE_DNS_MODE),
                    Settings.Secure.getUriFor(VPN_PRIVATE_DNS_DEFAULT_MODE),
                    Settings.Secure.getUriFor(VPN_PRIVATE_DNS_SPECIFIER),
            };
        }
    }

    private final NetworkType mNetworkType;
    private final GlobalOrUserId mTarget;
    private final Handler mHandler;
    private final ContentObserver mSettingsObserver;
    private final ConnectivityManager mConnectivityManager;
    private LinkProperties mLatestLinkProperties;
    private Preference mPreference;

    public PrivateDnsPreferenceController(Context context, NetworkType networkType) {
        super(context, getKey(networkType));
        mNetworkType = networkType;
        mTarget = mNetworkType == NetworkType.PHYSICAL ? GlobalOrUserId.GLOBAL :
                GlobalOrUserId.currentUserId();
        mHandler = new Handler(Looper.getMainLooper());
        mSettingsObserver = new PrivateDnsSettingsObserver(mHandler);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
    }

    @Override
    public String getPreferenceKey() {
        return getKey(mNetworkType);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!mContext.getResources().getBoolean(R.bool.config_show_private_dns_settings)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        if (mNetworkType == NetworkType.PHYSICAL) {
            final UserManager userManager = mContext.getSystemService(UserManager.class);
            if (userManager.isAdminUser()) return AVAILABLE;
            return DISABLED_FOR_USER;
        }
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onStart() {
        for (Uri uri : getSettingsUris(mNetworkType)) {
            if (mNetworkType == NetworkType.PHYSICAL) {
                mContext.getContentResolver().registerContentObserver(uri,false,
                        mSettingsObserver);
            } else {
                mContext.getContentResolver().registerContentObserverAsUser(uri, false,
                        mSettingsObserver, mTarget.getUserHandle());
            }
        }

        // ConnectivityManager callback is unregistered in onStop(), so if a network is lost in the
        // time between onStop() and onStart() then it will be missed and mLatestLinkProperties
        // will point to a stale object. Setting it to null avoids this occurring and it will
        // immediately get the latest LinkProperties (if they exist) when the CM callback is
        // registered and fires.
        mLatestLinkProperties = null;

        if (mNetworkType == NetworkType.PHYSICAL) {
            // The system default network is used by default for DNS (cleartext and private)
            // requests made by users that aren't under a VPN. This UI could be expanded to display
            // the private DNS connection status for each separate physical network, but for now it
            // will just show the default.
            mConnectivityManager.registerSystemDefaultNetworkCallback(mNetworkCallback, mHandler);
        } else {
            mConnectivityManager.registerNetworkCallback(new NetworkRequest.Builder()
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .build(), mNetworkCallback, mHandler);
        }
    }

    @Override
    public void onStop() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
    }

    @Override
    public CharSequence getSummary() {
        final Resources res = mContext.getResources();
        final int mode = ConnectivitySettingsManager.getPrivateDnsMode(mContext, mTarget);
        final LinkProperties lp = mLatestLinkProperties;
        final List<InetAddress> dnses = (lp == null) ? null : lp.getValidatedPrivateDnsServers();
        final boolean dnsesResolved = !ArrayUtils.isEmpty(dnses);
        switch (mode) {
            case PRIVATE_DNS_MODE_OFF:
                return res.getString(com.android.settingslib.R.string.private_dns_mode_off);
            case PRIVATE_DNS_MODE_OPPORTUNISTIC:
                return dnsesResolved ? res.getString(R.string.private_dns_mode_on)
                        : res.getString(
                                com.android.settingslib.R.string.private_dns_mode_opportunistic);
            case PRIVATE_DNS_MODE_PROVIDER_HOSTNAME:
                return dnsesResolved
                        ? ConnectivitySettingsManager.getPrivateDnsHostname(mContext, mTarget)
                        : res.getString(
                                com.android.settingslib.R.string.private_dns_mode_provider_failure);
        }
        return "";
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setEnabled(!isManagedByAdmin());
    }

    private boolean isManagedByAdmin() {
        if (mNetworkType == NetworkType.VPN) {
            return false;
        }
        EnforcedAdmin enforcedAdmin = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
                mContext, UserManager.DISALLOW_CONFIG_PRIVATE_DNS, UserHandle.myUserId());
        return enforcedAdmin != null;
    }

    private class PrivateDnsSettingsObserver extends ContentObserver {
        public PrivateDnsSettingsObserver(Handler h) {
            super(h);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mPreference != null) {
                updateState(mPreference);
            }
        }
    }

    private final NetworkCallback mNetworkCallback = new NetworkCallback() {
        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties lp) {
            Log.w("LPChangedDebug", "onLinkPropertiesChanged, network: " + network.getNetId() + ", interface: " + lp.getInterfaceName());
            mLatestLinkProperties = lp;
            if (mPreference != null) {
                updateState(mPreference);
            }
        }

        // For some reason this gets fired twice when there's a single physical network plus a VPN
        // network and the physical network gets disabled. Nothing breaks because of this.
        @Override
        public void onLost(Network network) {
            mLatestLinkProperties = null;
            if (mPreference != null) {
                updateState(mPreference);
            }
        }
    };
}
