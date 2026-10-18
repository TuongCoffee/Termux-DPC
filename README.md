# Termux:DPC

Small device owner app you control from Termux with `am broadcast`.

## Build
Push to GitHub. The **build** workflow makes a debug apk. Download it from the
Actions tab (artifact `termux-dpc-apk`).

## Install and set as device owner
1. Remove all accounts and extra users (`adb shell pm list users` shows only user 0).
2. Install the apk.
3. `adb shell dpm set-device-owner com.tuongcoffee.termuxdpc/.AdminReceiver`

## Set your token (once, right after step 3)
    am broadcast -n com.tuongcoffee.termuxdpc/.CmdReceiver --es action set_token --es token YOURTOKEN

## Use
    am broadcast -n com.tuongcoffee.termuxdpc/.CmdReceiver --es action camera_off --es token YOURTOKEN
    am broadcast -n com.tuongcoffee.termuxdpc/.CmdReceiver --es action camera_on --es token YOURTOKEN
    am broadcast -n com.tuongcoffee.termuxdpc/.CmdReceiver --es action lock --es token YOURTOKEN
    am broadcast -n com.tuongcoffee.termuxdpc/.CmdReceiver --es action reboot --es token YOURTOKEN
    am broadcast -n com.tuongcoffee.termuxdpc/.CmdReceiver --es action run --es token YOURTOKEN --es cmd 'ls -l /sdcard'

`run` takes optional `--es input 'text'` as stdin. Output comes back in `data="..."`.
Commands run as this app's own user (no root or shell privileges) and stop after 8 seconds.

## Remove
Before uninstalling, give up device owner:

    am broadcast -n com.tuongcoffee.termuxdpc/.CmdReceiver --es action clear_owner --es token YOURTOKEN

If you lose the token, `adb shell dpm remove-active-admin com.tuongcoffee.termuxdpc/.AdminReceiver`
also works for debug builds.
