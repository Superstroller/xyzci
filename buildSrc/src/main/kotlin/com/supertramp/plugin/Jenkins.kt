package com.supertramp.plugin

import com.supertramp.plugin.ext.JenkinsExtension
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.Project
import java.io.*

private var mFile : File? = null
private val mFileName = "apkname.properties"

fun clearApkName(project : Project, ext : JenkinsExtension?) {
    ext?.apply {
        try {
            val fileDir = File(project.rootDir, workspacePath)
            mFile = File(fileDir, mFileName)
            mFile?.takeIf { it.exists() }?.let {
                val fileWriter = FileWriter(it)
                fileWriter.write("")
                fileWriter.flush()
                fileWriter.close()
            }
        }catch (e : Exception) {}
    }
}

fun writeApkName(name : String, isTest : Boolean) {
    mFile?.takeIf { it.exists() }?.let {
        val fileWriter = FileWriter(it)
        fileWriter.write(if (isTest) "test:${name}\n" else "product:${name}\n")
        fileWriter.close()
    }
}

//从jenkins服务器上下载存储当前构建产物apk名称的文件
fun downloadApkNameFile(project: Project, ext : JenkinsExtension?, isTest: Boolean) {
    ext?.apply {
        val url = "${remotePath}${workspacePath}$mFileName"
        val credential = Credentials.basic(userName, apiToken)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .build()
        val response = OkHttpClient().newCall(request).execute()
        if (response.isSuccessful) {
            val dir = project.rootDir
            val file = File(dir, mFileName)
            file.takeIf { it.exists() }?.let {
                it.delete()
                it.createNewFile()
            }
            val fos = FileOutputStream(file)
            response.body()?.byteStream()?.let { ins ->
                var sum = 0
                val buf = ByteArray(2048)
                var len = ins.read(buf)
                while (len != -1) {
                    fos.write(buf, 0, len)
                    sum += len
                    len = ins.read(buf)
                }
                fos.flush()
                fos.close()
                ins.close()
                readApkName(project, ext, isTest)?.takeIf { it.isNotEmpty() }?.let {
                    downloadApk(project, ext, it, isTest)
                }
            }
        }
    }
}

//从下载下来的apk名称存储文件中读取apk名称
fun readApkName(project: Project, ext : JenkinsExtension?, isTest: Boolean) : String? {
    ext?.apply {
        try {
            val fileDir = File(project.rootDir, workspacePath)
            mFile = File(fileDir, mFileName)
            mFile?.takeIf { it.exists() }?.let {
                val fileReader = FileReader(mFile)
                val br = BufferedReader(fileReader)
                br.readLine()?.takeIf { it.isNotEmpty() }?.split(':')?.takeIf { it.size > 1 }?.let {
                    if ((it[0] == "test") and isTest) {
                        return it[1]
                    }
                    else if ((it[0] == "product") and !isTest) {
                        return it[1]
                    }
                }
                br.readLine()?.takeIf { it.isNotEmpty() }?.split(':')?.takeIf { it.size > 1 }?.let {
                    if ((it[0] == "test") and isTest) {
                        return it[1]
                    }
                    else if ((it[0] == "product") and !isTest) {
                        return it[1]
                    }
                }
            }
        }catch (e : Exception) {}
    }
    return null
}

//从jenkins服务器上下载apk
fun downloadApk(project : Project, ext : JenkinsExtension, apkName : String, isTest: Boolean) {
    ext?.apply {
        val url = "${remotePath}${workspacePath}${apkDir}$apkName"
        val credential = Credentials.basic(userName, apiToken)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .build()
        val response = OkHttpClient().newCall(request).execute()
        if (response.isSuccessful) {
            val dir = File(project.rootDir, localApkDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            dir.listFiles().forEach {
                if (it.isFile) it.delete()
            }
            val apkFile = File(dir, apkName)
            if (apkFile.exists()) apkFile.delete()
            apkFile.createNewFile()
            val fos = FileOutputStream(apkFile)
            response.body()?.byteStream()?.let { ins ->
                var sum = 0
                val buf = ByteArray(2048)
                var len = ins.read(buf)
                while (len != -1) {
                    fos.write(buf, 0, len)
                    sum += len
                    len = ins.read(buf)
                }
                fos.flush()
                fos.close()
                ins.close()
                println(if (isTest) "downloadTestApk successful" else "downloadProductApk successful")
            }
        }
        else {
            println(if (isTest) "downloadTestApk failed" else "downloadProductApk failed")
        }
    }
}