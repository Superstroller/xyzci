package com.supertramp.plugin.ext

open class JenkinsExtension {

    var userName = "chentingcai" //管理员用户名
    var apiToken = "11abc7a8a7aa52b1c8b98032362da85fb7" //管理员APIToken
    var triggerToken : String = "xyzcitrigger" //触发令牌token
    var triggerParam : String = "buildType"
    var remotePath : String = "" //jenkins服务器地址
    var workspacePath : String = "ws/"//工作空间目录
    var apkDir : String = "outapks/" //apk生成的目录
    var testApkName : String = "" //当前构建成功的测试服apk名称
    var productApkName : String = "" //生产apk名称
    var localApkDir : String = "outapks" //下载的apk存放的位置

}