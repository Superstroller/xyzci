package com.supertramp.plugin.ext

open class DingExtension {

    var enable : Boolean = false //控制功能是否生效
    var webHook : String = ""//钉钉机器人唯一识别
    var robotSecret : String = ""
    var appName : String = ""
    var testApkName : String = ""//测试环境
    var productApkName : String = ""//生产环境apk名称
    var apkServerPath : String = ""//apk在打包服务器上的路径
    var apkVersion : String = ""
    var atUser : String = ""//需要at的用户
    var atAll : Boolean = false
    var atMsg : String = "最新包"
    var jenkinsUsername : String = ""
    var jenkinsPassword : String = ""

}