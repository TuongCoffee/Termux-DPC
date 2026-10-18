package com.tuongcoffee.termuxdpc;

import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

public class CmdReceiver extends BroadcastReceiver {

	private void reply(String msg) {
		if (isOrderedBroadcast()) {
			setResultData(msg);
		}
	}

	private static boolean sameToken(String a, String b) {
		return java.security.MessageDigest.isEqual(
			a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
			b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static String need(String value, String name) throws Exception {
		if (value == null || value.isEmpty()) {
			throw new Exception("missing --es " + name);
		}
		return value;
	}

	private static void needSdk(int sdk) throws Exception {
		if (Build.VERSION.SDK_INT < sdk) {
			throw new Exception("needs android api " + sdk + ", this phone is " + Build.VERSION.SDK_INT);
		}
	}

	private static String readAll(java.io.InputStream in) {
		try {
			java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
			byte[] b = new byte[4096];
			int n;
			while ((n = in.read(b)) != -1) {
				buf.write(b, 0, n);
			}
			return buf.toString("UTF-8");
		} catch (Exception e) {
			return "";
		}
	}

	private void runCommand(Intent intent) {
		final PendingResult pending = goAsync();
		final String cmd = intent.getStringExtra("cmd");
		final String input = intent.getStringExtra("input");
		new Thread(() -> {
			String result;
			try {
				if (cmd == null) {
					throw new Exception("missing cmd");
				}
				final Process p = new ProcessBuilder("sh", "-c", cmd).start();
				Thread dog = new Thread(() -> {
					try {
						Thread.sleep(8000);
					} catch (InterruptedException e) {
					}
					p.destroy();
				});
				dog.setDaemon(true);
				dog.start();
				if (input != null) {
					p.getOutputStream().write(input.getBytes("UTF-8"));
				}
				p.getOutputStream().close();
				final String[] err = new String[1];
				Thread t = new Thread(() -> err[0] = readAll(p.getErrorStream()));
				t.start();
				String out = readAll(p.getInputStream());
				t.join();
				int code = p.waitFor();
				result = "exit=" + code + "\n--stdout--\n" + out + "\n--stderr--\n" + err[0];
			} catch (Exception e) {
				result = "error: " + e;
			}
			if (result.length() > 100000) {
				result = result.substring(0, 100000);
			}
			pending.setResultData(result);
			pending.finish();
		}).start();
	}

	private String handle(Context ctx, Intent intent, String action) throws Exception {
		DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
		ComponentName admin = new ComponentName(ctx, AdminReceiver.class);
		String pkg = intent.getStringExtra("pkg");
		String arg = intent.getStringExtra("arg");
		String arg2 = intent.getStringExtra("arg2");

		switch (action) {

			// ---------- info ----------
			case "info": {
				StringBuilder s = new StringBuilder();
				s.append("android api ").append(Build.VERSION.SDK_INT);
				s.append("\ndevice owner: ").append(dpm.isDeviceOwnerApp(ctx.getPackageName()));
				s.append("\nadmin active: ").append(dpm.isAdminActive(admin));
				s.append("\ncamera disabled: ").append(dpm.getCameraDisabled(admin));
				s.append("\nscreenshot disabled: ").append(dpm.getScreenCaptureDisabled(admin));
				s.append("\nkeyguard features: ").append(dpm.getKeyguardDisabledFeatures(admin));
				return s.toString();
			}
			case "restrictions": {
				Bundle b = dpm.getUserRestrictions(admin);
				java.util.TreeSet<String> names = new java.util.TreeSet<String>();
				for (String k : b.keySet()) {
					if (b.getBoolean(k)) {
						names.add(k);
					}
				}
				return names.isEmpty() ? "no restrictions set" : android.text.TextUtils.join("\n", names);
			}
			case "app_info": {
				need(pkg, "pkg");
				return "hidden: " + dpm.isApplicationHidden(admin, pkg)
					+ "\nsuspended: " + dpm.isPackageSuspended(admin, pkg)
					+ "\nuninstall blocked: " + dpm.isUninstallBlocked(admin, pkg);
			}
			case "perm_state": {
				need(pkg, "pkg");
				need(arg, "arg (permission)");
				int st = dpm.getPermissionGrantState(admin, pkg, arg);
				return st == DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED ? "granted"
					: st == DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED ? "denied" : "default";
			}

			// ---------- device ----------
			case "lock":
				dpm.lockNow();
				return "locked";
			case "reboot":
				dpm.reboot(admin);
				return "rebooting";
			case "wipe": {
				if (!"YES_WIPE".equals(intent.getStringExtra("confirm"))) {
					return "refused: add --es confirm YES_WIPE (this erases the phone)";
				}
				dpm.wipeData("sd".equals(arg) ? DevicePolicyManager.WIPE_EXTERNAL_STORAGE : 0);
				return "wiping";
			}
			case "clear_owner":
				dpm.clearDeviceOwnerApp(ctx.getPackageName());
				return "device owner removed";
			case "org_name":
				dpm.setOrganizationName(admin, need(arg, "arg (name)"));
				return "ok";

			// ---------- screen, camera, lock ----------
			case "camera_off":
				dpm.setCameraDisabled(admin, true);
				return "camera off";
			case "camera_on":
				dpm.setCameraDisabled(admin, false);
				return "camera on";
			case "screenshot_off":
				dpm.setScreenCaptureDisabled(admin, true);
				return "screenshots off";
			case "screenshot_on":
				dpm.setScreenCaptureDisabled(admin, false);
				return "screenshots on";
			case "statusbar_off":
				return "ok: " + dpm.setStatusBarDisabled(admin, true);
			case "statusbar_on":
				return "ok: " + dpm.setStatusBarDisabled(admin, false);
			case "keyguard_off":
				return "ok: " + dpm.setKeyguardDisabled(admin, true);
			case "keyguard_on":
				return "ok: " + dpm.setKeyguardDisabled(admin, false);
			case "keyguard_features":
				dpm.setKeyguardDisabledFeatures(admin, Integer.parseInt(need(arg, "arg (number, 0 = none, 2147483647 = all)")));
				return "ok";
			case "lock_timeout":
				dpm.setMaximumTimeToLock(admin, Long.parseLong(need(arg, "arg (milliseconds, 0 = no limit)")));
				return "ok";
			case "password_complexity": {
				needSdk(31);
				need(arg, "arg (none, low, medium, high)");
				int c = arg.equals("high") ? DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH
					: arg.equals("medium") ? DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM
					: arg.equals("low") ? DevicePolicyManager.PASSWORD_COMPLEXITY_LOW
					: DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
				dpm.setRequiredPasswordComplexity(c);
				return "ok";
			}
			case "mute_on":
				dpm.setMasterVolumeMuted(admin, true);
				return "muted";
			case "mute_off":
				dpm.setMasterVolumeMuted(admin, false);
				return "unmuted";
			case "location_on":
				needSdk(30);
				dpm.setLocationEnabled(admin, true);
				return "location on";
			case "location_off":
				needSdk(30);
				dpm.setLocationEnabled(admin, false);
				return "location off";

			// ---------- user restrictions (any name, e.g. no_usb_file_transfer) ----------
			case "restrict":
				dpm.addUserRestriction(admin, need(arg, "arg (restriction name)"));
				return "restricted: " + arg;
			case "unrestrict":
				dpm.clearUserRestriction(admin, need(arg, "arg (restriction name)"));
				return "allowed: " + arg;

			// ---------- apps ----------
			case "app_hide":
				return "ok: " + dpm.setApplicationHidden(admin, need(pkg, "pkg"), true);
			case "app_unhide":
				return "ok: " + dpm.setApplicationHidden(admin, need(pkg, "pkg"), false);
			case "app_suspend": {
				String[] failed = dpm.setPackagesSuspended(admin, new String[] { need(pkg, "pkg") }, true);
				return failed.length == 0 ? "suspended" : "failed: " + android.text.TextUtils.join(",", failed);
			}
			case "app_unsuspend": {
				String[] failed = dpm.setPackagesSuspended(admin, new String[] { need(pkg, "pkg") }, false);
				return failed.length == 0 ? "unsuspended" : "failed: " + android.text.TextUtils.join(",", failed);
			}
			case "app_enable":
				dpm.enableSystemApp(admin, need(pkg, "pkg"));
				return "ok";
			case "uninstall_block":
				dpm.setUninstallBlocked(admin, need(pkg, "pkg"), true);
				return "uninstall blocked";
			case "uninstall_allow":
				dpm.setUninstallBlocked(admin, need(pkg, "pkg"), false);
				return "uninstall allowed";
			case "protect":
				needSdk(30);
				dpm.setUserControlDisabledPackages(admin,
					java.util.Arrays.asList(need(arg, "arg (comma separated packages)").split(",")));
				return "protected (no force stop or clear data): " + arg;
			case "protect_clear":
				needSdk(30);
				dpm.setUserControlDisabledPackages(admin, new java.util.ArrayList<String>());
				return "protection cleared";

			// ---------- permissions ----------
			case "perm_grant":
				return "ok: " + dpm.setPermissionGrantState(admin, need(pkg, "pkg"),
					need(arg, "arg (permission)"), DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
			case "perm_deny":
				return "ok: " + dpm.setPermissionGrantState(admin, need(pkg, "pkg"),
					need(arg, "arg (permission)"), DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED);
			case "perm_default":
				return "ok: " + dpm.setPermissionGrantState(admin, need(pkg, "pkg"),
					need(arg, "arg (permission)"), DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
			case "perm_policy": {
				need(arg, "arg (prompt, grant, deny)");
				dpm.setPermissionPolicy(admin, arg.equals("grant") ? DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT
					: arg.equals("deny") ? DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY
					: DevicePolicyManager.PERMISSION_POLICY_PROMPT);
				return "ok";
			}

			// ---------- settings, time, network ----------
			case "global_set":
				dpm.setGlobalSetting(admin, need(arg, "arg (key)"), need(arg2, "arg2 (value)"));
				return "ok";
			case "secure_set":
				dpm.setSecureSetting(admin, need(arg, "arg (key)"), need(arg2, "arg2 (value)"));
				return "ok";
			case "timezone":
				needSdk(28);
				return "ok: " + dpm.setTimeZone(admin, need(arg, "arg (e.g. Asia/Ho_Chi_Minh)"));
			case "time":
				needSdk(28);
				return "ok: " + dpm.setTime(admin, Long.parseLong(need(arg, "arg (epoch milliseconds)")));
			case "private_dns":
				needSdk(29);
				need(arg, "arg (host name, or auto)");
				return "result code: " + (arg.equals("auto")
					? dpm.setGlobalPrivateDnsModeOpportunistic(admin)
					: dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, arg));
			case "account_block":
				dpm.setAccountManagementDisabled(admin, need(arg, "arg (account type, e.g. com.google)"), true);
				return "ok";
			case "account_allow":
				dpm.setAccountManagementDisabled(admin, need(arg, "arg (account type, e.g. com.google)"), false);
				return "ok";
			case "update": {
				need(arg, "arg (auto, postpone, clear)");
				if (arg.equals("auto")) {
					dpm.setSystemUpdatePolicy(admin, SystemUpdatePolicy.createAutomaticInstallPolicy());
				} else if (arg.equals("postpone")) {
					dpm.setSystemUpdatePolicy(admin, SystemUpdatePolicy.createPostponeInstallPolicy());
				} else {
					dpm.setSystemUpdatePolicy(admin, null);
				}
				return "ok";
			}

			default:
				return "unknown action: " + action;
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getStringExtra("action");
		String token = intent.getStringExtra("token");
		if (action == null || token == null) {
			reply("missing action or token");
			return;
		}

		SharedPreferences prefs = context.getSharedPreferences("dpc", Context.MODE_PRIVATE);
		String saved = prefs.getString("token", null);
		if (saved == null) {
			if (action.equals("set_token")) {
				prefs.edit().putString("token", token).apply();
				reply("token saved");
			} else {
				reply("no token set yet");
			}
			return;
		}
		if (!sameToken(saved, token)) {
			reply("wrong token");
			return;
		}

		try {
			if (action.equals("run")) {
				runCommand(intent);
			} else if (action.equals("new_token")) {
				String fresh = need(intent.getStringExtra("new"), "new");
				prefs.edit().putString("token", fresh).apply();
				reply("token changed");
			} else {
				reply(handle(context, intent, action));
			}
		} catch (Throwable e) {
			reply("error: " + e);
		}
	}
}
