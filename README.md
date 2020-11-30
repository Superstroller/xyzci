# xyzci
Continuous integration plugin for my company's projects

## Description
This is a tool for helping building an Android Apk convenient，we can trigger a building on jenkins server through it，and we can download the artifacts in this build through it. when the building completed,it will send an Ding message automatically.

## Adding to project
```groovy
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
Use the `jenkins` closure to set the info of your jenkins server config:

```groovy
jenkins {
  
}
```

Use the `ding` closure to set the info of the ding robot:

```groovy
ding {

}
```

Finally, use the task `buildApk` to trigger a building in the remote jenkins server.
