package com.tuongcoffee.termuxdpc;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class CmdReceiver extends BroadcastReceiver {

	private void reply(String msg) {
		if (isOrderedBroadcast()) {
			setResultData(msg);
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
		if (!saved.equals(token)) {
			reply("wrong token");
			return;
		}

		DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
		ComponentName admin = new ComponentName(context, AdminReceiver.class);

		switch (action) {
			case "camera_off":
				dpm.setCameraDisabled(admin, true);
				reply("camera off");
				break;
			case "camera_on":
				dpm.setCameraDisabled(admin, false);
				reply("camera on");
				break;
			case "lock":
				dpm.lockNow();
				reply("locked");
				break;
			case "reboot":
				dpm.reboot(admin);
				break;
			case "run":
				runCommand(intent);
				break;
			case "clear_owner":
				dpm.clearDeviceOwnerApp(context.getPackageName());
				reply("device owner removed");
				break;
			default:
				reply("unknown action");
		}
	}
}
