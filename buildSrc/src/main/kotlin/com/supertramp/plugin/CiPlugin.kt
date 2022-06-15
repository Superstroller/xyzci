package com.supertramp.plugin

import com.android.build.gradle.AppExtension
import com.google.gson.Gson
import com.supertramp.plugin.ding.ActionBtnBean
import com.supertramp.plugin.ding.ActionCardBean
import com.supertramp.plugin.ding.AtBean
import com.supertramp.plugin.ding.DingTalkBean
import com.supertramp.plugin.ding.TextBean
import com.supertramp.plugin.ext.DingExtension
import com.supertramp.plugin.ext.JenkinsExtension
import com.supertramp.plugin.ext.MappingExtension
import okhttp3.*
import org.apache.commons.codec.binary.Base64.encodeBase64
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.ArrayList

class CiPlugin : Plugin<Project> {

    private var mDingExtension : DingExtension? = null
    private var mJenkinsExtension : JenkinsExtension? = null
    private var mMappingExtension : MappingExtension? = null
    private val mGradleArgs = StringBuilder()
    private val mGson = Gson()

    override fun apply(project : Project) {
        mJenkinsExtension = project.extensions.create("jenkins", JenkinsExtension::class.java)
        mDingExtension = project.extensions.create("ding", DingExtension::class.java)
        mMappingExtension = project.extensions.create("mapping", MappingExtension::class.java)

        val requests = project.gradle.startParameter.taskRequests
        if (requests.size == 0) return
        requests[0].args.forEach {
            mGradleArgs.append(it)
        }

        createTasks(project)
        configData(project)

        project.gradle.buildFinished {
            mDingExtension?.takeIf { it.enable }?.let {
                sendRobotMsg()
            }
            mMappingExtension?.takeIf { it.enable }?.let {
                uploadMapping(it)
            }
        }
    }

    private fun configData(project: Project) {
        project.gradle.taskGraph.whenReady {
            if (project.rootProject.hasProperty("ding")) {
                mDingExtension?.enable = true
                clearApkName(project, mJenkinsExtension)
                project.extensions.getByName("android")?.let {
                    try {
                        it as AppExtension
                    }catch (e : Exception) {
                        null
                    }
                }?.let {
                    it.applicationVariants
                }?.let {variants ->
                    variants.all { variant ->
                        if (mGradleArgs.contains(variant.name, true)) {
                            mDingExtension?.apkVersion = variant.versionName
                            variant.outputs.forEach { output ->
                                if (output.outputFile != null && output.outputFile.name.endsWith("apk")) {
                                    val fileName = output.outputFile.name
                                    when {
                                        fileName.contains("test", true) -> {
                                            mDingExtension?.testApkName = fileName
                                            writeApkName(project, fileName, true)
//                                            val productApkName = fileName.replace("Test", "Product")
//                                            mDingExtension?.productApkName = productApkName
//                                            writeApkName(project, productApkName, false)
                                        }
                                        fileName.contains("product", true) -> {
                                            mDingExtension?.productApkName = fileName
                                            writeApkName(project, fileName, false)
                                            if (project.rootProject.hasProperty("testBuildTime")) {
                                                val testBuildTime = project.rootProject.property("testBuildTime").toString()
                                                val testApkName = fileName.replace("Product", "Test")
                                                val index = testApkName.lastIndexOf('_')
                                                mDingExtension?.testApkName = testApkName.replaceRange(index - 15, index, testBuildTime)
                                                writeApkName(project, testApkName, true)
                                            }
                                        }
                                        else -> {
                                            mDingExtension?.testApkName = fileName
                                            writeApkName(project, output.outputFile.name, true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!project.rootProject.hasProperty("mapping")) {
                mMappingExtension?.enable = false
            }
        }
    }

    private fun createTasks(project : Project) {
        project.tasks.create("buildAllApk") {
            it.group = "jenkins"
            it.doLast {
                triggerBuild("all")
            }
        }
        project.tasks.create("buildTestApk") {
            it.group = "jenkins"
            it.doLast {
                triggerBuild("test")
            }
        }

        project.tasks.create("buildProductApk") {
            it.group = "jenkins"
            it.doLast {
                triggerBuild("product")
            }
        }

//        project.tasks.create("downloadTestApk") {
//            it.group = "jenkins"
//            it.doLast {
//                downloadApkFromJenkins(project, true)
//            }
//        }
//
//        project.tasks.create("downloadProductApk") {
//            it.group = "jenkins"
//            it.doLast {
//                downloadApkFromJenkins(project, false)
//            }
//        }
    }

    //触发构建
    private fun triggerBuild(buildType : String) {
        mJenkinsExtension?.apply {
            val url = "${remotePath}buildWithParameters?token=$triggerToken&${triggerParam}=${buildType}"
            val credential = Credentials.basic(userName, apiToken)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", credential)
                .build()
            OkHttpClient().newCall(request).execute()
        }
    }

    //下载构建产物apk
    private fun downloadApkFromJenkins(project : Project, isTest : Boolean) {
        downloadApkNameFile(project, mJenkinsExtension, isTest)
    }

    //发送钉钉机器人通知
    private fun sendRobotMsg() {
        mDingExtension?.apply {
            val dingTalk = DingTalkBean()
            dingTalk.msgtype= "actionCard"
            val actionCard = ActionCardBean()
            actionCard.btnOrientation = "1"
            val builder = StringBuilder("### $appName \n #### 版本 $apkVersion")
            if (jenkinsUsername.isNotEmpty() && jenkinsPassword.isNotEmpty()) {
                builder.append("\n #### 用户名 ${jenkinsUsername}\n #### 密码 $jenkinsPassword")
            }
            actionCard.text = builder.toString()
            val btns = ArrayList<ActionBtnBean>()
            if (testApkName.isNotEmpty()) {
                val btn = ActionBtnBean()
                btn.title = "下载测试环境安装包"
                btn.actionURL = "${apkServerPath}${testApkName}"
                btns.add(btn)
            }
            if (productApkName.isNotEmpty()) {
                val btn = ActionBtnBean()
                btn.title = "下载正式环境安装包"
                btn.actionURL = "${apkServerPath}${productApkName}"
                btns.add(btn)
            }
            actionCard.btns = btns
            dingTalk.actionCard = actionCard

            val json = mGson.toJson(dingTalk)
            val url = "$webHook${getSign(robotSecret)}"

            val client = OkHttpClient()
            val body = FormBody.create(MediaType.parse("application/json; charset=utf-8"), json)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
            client.newCall(request).execute()
            if (atUser.isNotEmpty() or atAll) {
                sendAtMsg(url, client)
            }
        }
    }

    //at某人
    private fun sendAtMsg(url : String, client : OkHttpClient) {
        mDingExtension?.apply {
            val dingTalk = DingTalkBean()
            dingTalk.msgtype = "text"
            val text = TextBean()
            text.content = "$atMsg"
            val at = AtBean()
            at.isAtAll = atAll
            at.atMobiles = atUser.split(",")
            dingTalk.text = text
            dingTalk.at = at
            val json = mGson.toJson(dingTalk)
            val body = FormBody.create(MediaType.parse("application/json; charset=utf-8"), json)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
            client.newCall(request).execute()
        }
    }

    //钉钉机器人安全设置获取签名
    private fun getSign(secret : String) : String {
        val timestamp = System.currentTimeMillis()
        val stringToSign = "${timestamp}\n$secret"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signData = mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
        val sign =  URLEncoder.encode(String(encodeBase64(signData)),"UTF-8")
        return "&timestamp=${timestamp}&sign=$sign"
    }

    //上传mapping文件到bugly服务器
    private fun uploadMapping(mapping : MappingExtension) {
        if (mapping.enable &&
            File(mapping.buglyJarPath).exists() &&
            File(mapping.mappingPath).exists()) {
            val process = Runtime.getRuntime().exec("${mapping.javaPath} " +
                    "-jar ${mapping.buglyJarPath} " +
                    "-appid ${mapping.appId} " +
                    "-appkey ${mapping.appKey} " +
                    "-bundleid ${mapping.pkgName} " +
                    "-version ${mapping.appVersion} " +
                    "-platform Android " +
                    "-inputMapping ${mapping.mappingPath}")
            process?.waitFor()
            printCmdResult(process)
        }
    }

    //打印命令执行结果
    private fun printCmdResult(process: Process) {
        try {
            val br = BufferedReader(InputStreamReader(process.inputStream))
            val sb = StringBuffer()
            var line : String?
            while (br.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            val result = sb.toString()
            println(result)
        }catch (e : Exception) {}
    }

}