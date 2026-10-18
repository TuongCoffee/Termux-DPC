# Termux:DPC

Small device owner app you control from Termux with `am broadcast`.

## Build
Push to GitHub. The **build** workflow makes a debug apk (artifact `termux-dpc-apk`
on the Actions tab). The apk is always signed with the key in `app/debug.keystore`,
so a new build installs over the old one.

## Install and set as device owner
1. Remove all accounts and extra users (`adb shell pm list users` shows only user 0).
2. Install the apk.
3. `adb shell dpm set-device-owner com.tuongcoffee.termuxdpc/.AdminReceiver`

## Set your token (once, right after step 3)
    am broadcast -n com.tuongcoffee.termuxdpc/.CmdReceiver --es action set_token --es token "$(cat ~/.dpc_token)"

## Command shape
    am broadcast -n com.tuongcoffee.termuxdpc/.CmdReceiver --es action ACTION --es token "$(cat ~/.dpc_token)" [--es pkg PACKAGE] [--es arg VALUE] [--es arg2 VALUE]

The answer is in `data="..."`. Errors come back as `error: ...`.

## Actions
Info
- `info`, `restrictions`, `app_info` (pkg), `perm_state` (pkg, arg=permission)

Device
- `lock`, `reboot`, `clear_owner`, `org_name` (arg)
- `wipe`: erases the phone. Needs `--es confirm YES_WIPE`. `--es arg sd` also wipes the sd card.
- `new_token`: `--es new NEWTOKEN` changes the token
- `run`: `--es cmd 'command'` (+ optional `--es input 'stdin text'`). Runs as this app's user, stops after 8 s.

Screen, camera, lock
- `camera_off` / `camera_on`, `screenshot_off` / `screenshot_on`
- `statusbar_off` / `statusbar_on`, `keyguard_off` / `keyguard_on`
- `keyguard_features` (arg = number, 0 none, 2147483647 all)
- `lock_timeout` (arg = ms), `password_complexity` (arg = none, low, medium, high)
- `mute_on` / `mute_off`, `location_on` / `location_off`

Restrictions (arg = any user restriction name)
- `restrict`, `unrestrict`
- names: no_usb_file_transfer, no_factory_reset, no_safe_boot, no_install_apps,
  no_uninstall_apps, no_config_wifi, no_config_bluetooth, no_bluetooth, no_add_user,
  no_debugging_features, no_outgoing_calls, no_sms, no_config_tethering, no_config_vpn,
  no_config_location, no_config_date_time, no_airplane_mode, no_network_reset,
  no_modify_accounts, no_adjust_volume, no_unmute_microphone, no_install_unknown_sources ...

Apps (pkg = package name)
- `app_hide` / `app_unhide`, `app_suspend` / `app_unsuspend`, `app_enable`
- `uninstall_block` / `uninstall_allow`
- `protect` (arg = comma separated packages, no force stop or clear data), `protect_clear`

Permissions
- `perm_grant`, `perm_deny`, `perm_default` (pkg + arg = permission, e.g. android.permission.CAMERA)
- `perm_policy` (arg = prompt, grant, deny)

Settings, time, network
- `global_set`, `secure_set` (arg = key, arg2 = value; android only allows some keys)
- `timezone` (arg = e.g. Asia/Ho_Chi_Minh), `time` (arg = epoch ms)
- `private_dns` (arg = host name, or auto)
- `account_block` / `account_allow` (arg = account type, e.g. com.google)
- `update` (arg = auto, postpone, clear)

## Not possible with a device owner app
Power off (only `reboot`), arbitrary root commands, reading other apps' private data.
Use Shizuku + rish in Termux for those.

## Remove
Before uninstalling, give up device owner:

    am broadcast -n com.tuongcoffee.termuxdpc/.CmdReceiver --es action clear_owner --es token "$(cat ~/.dpc_token)"

If you lose the token, `adb shell dpm remove-active-admin com.tuongcoffee.termuxdpc/.AdminReceiver`
also works for debug builds.
