import 'package:device_apps/device_apps.dart';
import 'package:flutter/material.dart';

void main() => runApp(MaterialApp(home: ListAppsPages()));

class ListAppsPages extends StatefulWidget {
  @override
  _ListAppsPagesState createState() => _ListAppsPagesState();
}

class _ListAppsPagesState extends State<ListAppsPages> {
  bool _showSystemApps = true;
  bool _onlyLaunchableApps = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text("Installed applications"),
        actions: <Widget>[
          PopupMenuButton(
            itemBuilder: (context) {
              return <PopupMenuItem<String>>[
                PopupMenuItem<String>(
                    value: 'system_apps', child: Text('Toggle system apps')),
                PopupMenuItem<String>(
                  value: "launchable_apps",
                  child: Text('Toggle launchable apps only'),
                )
              ];
            },
            onSelected: (key) {
              if (key == "system_apps") {
                setState(() {
                  _showSystemApps = !_showSystemApps;
                });
              }
              if (key == "launchable_apps") {
                setState(() {
                  _onlyLaunchableApps = !_onlyLaunchableApps;
                });
              }
            },
          )
        ],
      ),
      body: _ListAppsPagesContent(
          includeSystemApps: _showSystemApps,
          onlyAppsWithLaunchIntent: _onlyLaunchableApps,
          key: GlobalKey()),
    );
  }
}

class _ListAppsPagesContent extends StatelessWidget {
  final bool includeSystemApps;
  final bool onlyAppsWithLaunchIntent;

  const _ListAppsPagesContent(
      {Key key,
      this.includeSystemApps: false,
      this.onlyAppsWithLaunchIntent: false})
      : super(key: key);

  @override
  Widget build(BuildContext context) {
    return FutureBuilder(
        future: DeviceApps.getInstalledApplications(
            includeAppIcons: true,
            includeAppBanners: true,
            includeSystemApps: includeSystemApps,
            onlyAppsWithLaunchIntent: onlyAppsWithLaunchIntent),
        builder: (context, data) {
          if (data.data == null) {
            return Center(child: CircularProgressIndicator());
          } else {
            List<Application> apps = data.data;
            print(apps);
            return ListView.builder(
                itemBuilder: (context, position) {
                  Application app = apps[position];
                  return Focus(
                    autofocus: true,
                    child: Column(
                    children: <Widget>[
                        Container(
                          child: ListTile(
                            leading: app.banner != null ? CircleAvatar(
                                            backgroundImage: MemoryImage(app.banner),
                                            backgroundColor: Colors.grey,
                                          )

                                : null,
                            onTap: () => DeviceApps.openApp(app.packageName),
                            title: Text("${app.appName} (${app.packageName})"),
                            subtitle: Text('Version: ${app.versionName}\nSystem app: ${app.systemApp}\nAPK file path: ${app.apkFilePath}\nData dir : ${app.dataDir}\nInstalled: ${DateTime.fromMillisecondsSinceEpoch(app.installTimeMilis).toString()}\nUpdated: ${DateTime.fromMillisecondsSinceEpoch(app.updateTimeMilis).toString()}'),
                          ),
                        ),
                        Divider(
                          height: 1.0,
                        )
                      ],
                    ),
                  );
                },
                itemCount: apps.length);
          }
        });
  }
}
