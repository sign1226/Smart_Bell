package com.smartbell.pro;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.content.Intent;
import android.content.Context;

@CapacitorPlugin(name = "WireGuard")
public class WireGuardPlugin extends Plugin {

    @PluginMethod
    public void setTunnelUp(PluginCall call) {
        String name = call.getString("name");
        if (name == null) {
            call.reject("Tunnel name is required");
            return;
        }

        Intent intent = new Intent("com.wireguard.android.action.SET_TUNNEL_UP");
        intent.setPackage("com.wireguard.android");
        intent.putExtra("tunnel", name);
        getContext().sendBroadcast(intent);

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void setTunnelDown(PluginCall call) {
        String name = call.getString("name");
        if (name == null) {
            call.reject("Tunnel name is required");
            return;
        }

        Intent intent = new Intent("com.wireguard.android.action.SET_TUNNEL_DOWN");
        intent.setPackage("com.wireguard.android");
        intent.putExtra("tunnel", name);
        getContext().sendBroadcast(intent);

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }
}
