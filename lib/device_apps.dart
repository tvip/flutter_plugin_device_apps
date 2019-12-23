import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'package:collection/collection.dart';

import 'package:flutter/services.dart';

class DeviceApps {
  static const MethodChannel _channel =
      const MethodChannel('g123k/device_apps');

  static Future<List<Application>> getInstalledApplications(
      {bool includeSystemApps: false,
      bool includeAppIcons: false,
      bool includeAppBanners: false,
      bool onlyAppsWithLaunchIntent: false}) async {
    return _channel.invokeMethod('getInstalledApps', {
      'system_apps': includeSystemApps,
      'include_app_icons': includeAppIcons,
      'include_app_banners': includeAppBanners,
      'only_apps_with_launch_intent': onlyAppsWithLaunchIntent
    }).then((apps) {
      if (apps != null && apps is List) {
        List<Application> list = new List();
        for (var app in apps) {
          if (app is Map) {
            try {
              list.add(Application(app));
            } catch (e) {
              if (e is AssertionError) {
                print('[DeviceApps] Unable to add the following app: $app');
                print('[DeviceApps] $e');
              } else {
                print('[DeviceApps] $e');
              }
            }
          }
        }

        return list;
      } else {
        return List<Application>(0);
      }
    }).catchError((err) {
      print(err);
      return List<Application>(0);
    });
  }

  static Future<Application> getApp(String packageName,
      [
        bool includeAppIcon = false,
        bool includeAppBanner = false
      ]) async {
    if (packageName.isEmpty) {
      throw Exception('The package name can not be empty');
    }

    return _channel.invokeMethod('getApp', {
      'package_name': packageName,
      'include_app_icon': includeAppIcon,
      'include_app_banner': includeAppBanner
    }).then((app) {
      if (app != null && app is Map) {
        return Application(app);
      } else {
        return null;
      }
    }).catchError((err) {
      print(err);
      return null;
    });
  }

  static Future<bool> isAppInstalled(String packageName) async {
    if (packageName.isEmpty) {
      throw Exception('The package name can not be empty');
    }

    bool isAppInstalled = await _channel
        .invokeMethod('isAppInstalled', {'package_name': packageName});
    return isAppInstalled;
  }

  static Future<bool> openApp(String packageName, {String className}) async {
    if (packageName.isEmpty) {
      throw Exception('The package name can not be empty');
    }
    try {
      return await _channel
          .invokeMethod('openApp', <String, dynamic>{'package_name': packageName, 'class_name': className}).catchError(print);
    } catch (e) {
      print("Error: $e");
      return false;
    }
  }
}

class Application {
  final String appName;
  final String apkFilePath;
  final String packageName;
  final String versionName;
  final int versionCode;
  final String dataDir;
  final bool systemApp;
  final int installTimeMilis;
  final int updateTimeMilis;
  final Uint8List icon;
  final Uint8List banner;

  factory Application(Map map) {
    if (map == null || map.length == 0) {
      throw Exception('The map can not be null!');
    }

    return Application._fromMap(map);
  }

  Application._fromMap(Map map)
      :
        appName = map['app_name'],
        apkFilePath = map['apk_file_path'],
        packageName = map['package_name'],
        versionName = map['version_name'],
        versionCode = map['version_code'],
        dataDir = map['data_dir'],
        systemApp = map['system_app'],
        icon = map['app_icon'],
        banner = map['app_banner'],
        installTimeMilis = map['install_time'],
        updateTimeMilis = map['update_time'];

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
          other is Application &&
              runtimeType == other.runtimeType &&
              appName == other.appName &&
              apkFilePath == other.apkFilePath &&
              packageName == other.packageName &&
              versionName == other.versionName &&
              versionCode == other.versionCode &&
              dataDir == other.dataDir &&
              systemApp == other.systemApp &&
              installTimeMilis == other.installTimeMilis &&
              updateTimeMilis == other.updateTimeMilis &&
              const ListEquality().equals(icon, other.icon) &&
              const ListEquality().equals(banner, other.banner);

  @override
  int get hashCode =>
      appName.hashCode ^
      apkFilePath.hashCode ^
      packageName.hashCode ^
      versionName.hashCode ^
      versionCode.hashCode ^
      dataDir.hashCode ^
      systemApp.hashCode ^
      installTimeMilis.hashCode ^
      updateTimeMilis.hashCode ^
      const ListEquality().hash(icon) ^
      const ListEquality().hash(banner);

  @override
  String toString() {
    return 'App name: $appName, Package name: $packageName, Version name: $versionName, Version code: $versionCode';
  }
}
