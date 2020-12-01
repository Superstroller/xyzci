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
import okhttp3.*
import org.apache.commons.codec.binary.Base64.encodeBase64
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.lang.StringBuilder
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.ArrayList

class CiPlugin : Plugin<Project> {

    private var mDingExtension : DingExtension? = null
    private var mJenkinsExtension : JenkinsExtension? = null
    private val mGradleArgs = StringBuilder()
    private val mGson = Gson()

    override fun apply(project : Project) {
        mJenkinsExtension = project.extensions.create("jenkins", JenkinsExtension::class.java)
        mDingExtension = project.extensions.create("ding", DingExtension::class.java)

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
                                    if (variant.name.contains("test", true)) {
                                        mDingExtension?.testApkName = output.outputFile.name
                                        writeApkName(project, output.outputFile.name, true)
                                    }
                                    else if (variant.name.contains("product", true)) {
                                        mDingExtension?.productApkName = output.outputFile.name
                                        writeApkName(project, output.outputFile.name, false)
                                    }
                                    else {
                                        mDingExtension?.testApkName = output.outputFile.name
                                        writeApkName(project, output.outputFile.name, true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createTasks(project : Project) {
        project.tasks.create("buildApk") {
            it.group = "jenkins"
            it.doLast {
                triggerBuild()
            }
        }

        project.tasks.create("downloadTestApk") {
            it.group = "jenkins"
            it.doLast {
                downloadApkFromJenkins(project, true)
            }
        }

        project.tasks.create("downloadProductApk") {
            it.group = "jenkins"
            it.doLast {
                downloadApkFromJenkins(project, false)
            }
        }
    }

    //触发构建
    private fun triggerBuild() {
        mJenkinsExtension?.apply {
            val url = "${remotePath}build?token=$triggerToken"
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
            actionCard.text = "### $appName \n #### 版本${apkVersion}"
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

}