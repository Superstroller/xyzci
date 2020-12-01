# xyzci
目前公司内Android工程的持续集成都可以用该插件优化。

## Description
提交代码后，可以通过gradle buildApk直接本地触发远程构建，并且构建成功后自动发送钉钉消息到群里。收到消息后可直接在钉钉群里点击消息下载apk，
也可以通过downloadProductApk和downloadTestApk这两个任务下载服务器上的apk，apk会被下载到outapks目录下。
插件会生成三个任务，在Android Studio右侧gradle视图窗口Tasks目录下的jenkins组中：buildApk, downloadProductApk, downloadTestApk, 可直接点击启动任务。

## Adding to project
```groovy
apply plugin : 'xyz-ci'
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.supertramp.plugin:xyzci:<latest-version>'
    }
}
```

## Simple usage
首先配置jenkins:
在jenkins的配置页选中构建触发器的第一项，并填写身份验证令牌，完成后保存。该令牌下面代码配置过程中会使用

Use the `jenkins` closure to set the info of the ding robot:
```groovy
jenkins {
  username = 'jenkins用户名'
  apiToken = '用户apitoken'//用于身份验证
  triggerToken = 'jenkins触发令牌token'//用于触发远程构建
  remotePath = 'jenkins服务器上对应项目的远程路径'
  workspacePath = 'jenkins上项目工作空间路径'
}
```

在测试和开发群中点击群设置->智能群助手->添加机器人->选择自定义->安全设置中选择加签（此处会生成密钥robotSecret）->完成（此处会生成webHook）
Use the `ding` closure to set the info of the ding robot:

```groovy
ding {
    weHook = '钉钉群中配置的机器人中获取'
    robotSecret = '同上，机器人获取'
    appName = '当前项目app名称'
    apkServerPath = 'jenkins打包成功后apk在jenkins服务器上的生成路径'
    atUser = '想要钉钉at对象的手机号（多人用逗号隔开）'
    atAll = '是否at所有人'
    atMsg = 'at对方后的留言'
}
```

Finally, use the task `buildApk` to trigger a building in the remote jenkins server, the task downloadProductApk and downloadTestApk to download the apk generated in the jenkins server.
