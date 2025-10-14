// Copyright (c) 2015, Xyrality
// All rights reserved.
// Modified for TreasureBoat by Plotters, August 2016

package org.treasureboat.gradle

import org.gradle.api.*
import groovyx.net.http.RESTClient
import groovy.util.slurpersupport.GPathResult
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project

import static groovyx.net.http.ContentType.URLENC
import static groovyx.net.http.ContentType.JSON
import org.gradle.api.tasks.bundling.*
import org.gradle.api.tasks.*
import org.apache.commons.io.FileUtils


buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath 'org.codehaus.groovy.modules.http-builder:http-builder:0.7.1'
        classpath 'commons-io:commons-io:2.6'
    }
}

class TBApplication extends WOProject {

    File applicationOutputDir
    File distributionsOutputDir
    String woaName
    String woaVersionedName
    String TBExecutable


    void apply(Project project) {
        super.apply(project)

        def extraVersion = System.getProperty('extraVersion')
        if (extraVersion != null) {
            if (extraVersion != "") {
                project.version = project.version + '-' + System.getProperty('extraVersion')
            }
        } else {
            project.version = project.version + project.versionNameSuffix
        }

        woaName = project.name + '.woa'
        woaVersionedName = project.name + '-' + project.version + '.woa'
        TBExecutable = project.name + '-' + project.version
        applicationOutputDir = new File(project.buildDir, woaName)
        distributionsOutputDir = new File(project.buildDir, 'distributions')

        configureEclipseProject(project)
        configureWOApplicationTasks(project)

        project.afterEvaluate {
            configureDeployTasks(project)
        }

//        project.build.dependsOn project.TBApplicationTarGz
//        project.build.dependsOn project.TBWebServerTarGz
        project.test.dependsOn project.woapplication
    }

    def configureDeployTasks(Project project) {
        def deploymentServers = project.treasureboat.deploymentServers
        def deploymentWebserver = project.treasureboat.deploymentWebserver
        def deploymentPath = project.treasureboat.deploymentPath
        def webserverResourcePath = project.treasureboat.webserverResourcePath
        def deploymentSSHUser = project.treasureboat.deploymentSSHUser
        def deploymentWebserverSSHUser = project.treasureboat.deploymentWebserverSSHUser
        def deploymentMonitorBounceTasks = project.treasureboat.deploymentMonitorBounceTasks
        def deploymentSSHPort = project.treasureboat.deploymentSSHPort
        def deploymentSSHIgnoreHostKey = project.treasureboat.deploymentSSHIgnoreHostKey

        if (!deploymentPath.endsWith('/')) {
            deploymentPath += '/'
        }

        if (!webserverResourcePath.endsWith('/')) {
            webserverResourcePath += '/'
        }

        if (!deploymentWebserver.endsWith('/')) {
            webserverResourcePath += '/'
        }

        if (deploymentServers.size() != 0) {
            if (deploymentPath.length() == 0 || webserverResourcePath.length() == 0 || deploymentSSHUser.length() == 0
                    || deploymentWebserver.length() == 0 || deploymentWebserverSSHUser.length() == 0) {
                throw new InvalidUserDataException('Need to specify deploymentPath, webserverResourcePath, deploymentSSHUser, deploymentWebserverSSHUser and deploymentWebserver when specifying deploymentServers')
            }

            project.with {
                task('copyToServers') {
                    // empty task, only to be used for dependencies
                    description 'collection task to copy to all stage servers'
                }

                for (int i = 0; i < deploymentServers.size(); i++) {
                    def deploymentServer = deploymentServers[i]
                    def copyTaskName1 = 'copyToServerWoa-' + deploymentServer
                    def copyTaskName2 = 'copyToServerWsR-' + deploymentServer
                    def destinationPath = deploymentPath + woaVersionedName    // destination path for woa binary
                    def webserverTarGz = project.name + '-WebServerResources-' + project.version + '.tar.gz'
                    def taskDescription1 = 'copies the WOA binary to ' + deploymentServer
                    def taskDescription2 = 'copies the WebServer Resources to ' + deploymentServer
                    def additionalSSHParameters = ''

                    if (deploymentSSHIgnoreHostKey) {
                        additionalSSHParameters = '-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no'
                    }

                    task(copyTaskName1, type: Exec, dependsOn: TBApplicationTarGz) {
                        description = taskDescription1
                        workingDir buildDir
                        // Zip woa and copy to server via ssh
                        def sshCommand = String.format('tar -C "%s" -zcf - ./ | ssh %s -p %d %s@%s "mkdir -p %s && tar -C %s -zxf -"', woaName, additionalSSHParameters, deploymentSSHPort, deploymentSSHUser, deploymentServer, destinationPath, destinationPath)
                        commandLine 'bash', '-c', sshCommand
                    }

                    task(copyTaskName2, type: Exec, dependsOn: TBWebServerTarGz) {
                        description = taskDescription2
                        workingDir distributionsOutputDir
                        // Copy WebServer Resources to server via ssh
                        def sshCommand2 = String.format('cat %s | ssh -p %d %s@%s "cd %s ; tar -zxvf -"', webserverTarGz, deploymentSSHPort, deploymentWebserverSSHUser, deploymentWebserver, webserverResourcePath)
                        commandLine 'bash', '-c', sshCommand2
                    }

                    project.copyToServers.dependsOn(copyTaskName1, copyTaskName2)
                }
            }

            if (deploymentMonitorBounceTasks.size() > 0) {
                project.with {
                    task('deployToServers', dependsOn: 'copyToServers') {
                        // empty task, only to be used for dependencies
                        description 'collection task to deploy to all stage servers'
                    }

                    for (keyValuePair in deploymentMonitorBounceTasks) {
                        def serverName = keyValuePair.key
                        def appNameArray = keyValuePair.value instanceof String ? [keyValuePair.value] : keyValuePair.value
                        def unixPath = deploymentPath + woaVersionedName + '-' + project.name
                        def copyTaskName = 'copyToServer-' + serverName

                        for (appName in appNameArray) {
                            def taskName = 'deployToServer-' + serverName + '-' + appName;
                            task(taskName, dependsOn: copyTaskName) {
                                description 'deploys the binary to ' + serverName + ' as application ' + appName
                                doLast {
                                    bounceWOApplication(logger, String.format(project.treasureboat.deploymentMonitorURLPattern, serverName), unixPath, appName)
                                }
                            }

                            project.deployToServers.dependsOn(taskName)
                        }
                    }
                }
            }
        }
    }

    def bounceWOApplication(logger, monitorURL, unixPath, appName) {
        logger.info "TBMonitor >> Updating TBMonitor at $monitorURL"
        logger.info "TBMonitor >> Executable path is $unixPath"
        logger.info "TBMonitor >> Application name is $appName"

        def monitor = new RESTClient(monitorURL)

        logger.info "TBMonitor >> Setting executable path to $unixPath..."
        def responseSet = monitor.put(path: 'ra/mApplications/' + appName + '.json', contentType: JSON, requestContentType: URLENC, body: "{unixPath:'$unixPath'}")
        assert responseSet.status == 200
        logger.info 'TBMonitor >> Set executable path SUCCESSFUL'

        logger.info 'TBMonitor >> Bouncing application...'
        def responseBounce = monitor.get(path: 'admin/bounce', query: [type:'app', name:appName])
        assert responseBounce.status == 200
        logger.info 'TBMonitor >> Bounce command SUCCESSFUL'

        logger.info 'TBMonitor >> Disabling autorecover...'
        def responseAutorecoverOff = monitor.get(path: 'admin/turnAutoRecoverOff', query: [type:'app', name:appName])
        assert responseAutorecoverOff.status == 200
        logger.info 'TBMonitor >> Autorecover off command SUCCESSFUL'
    }

    // *** task: woapplication ***
    def configureWOApplicationTasks(Project project) {
        project.with {
            task('woapplication', dependsOn: [classes, copyDependencies]) {
                description 'Build this framework as TreasureBoat application'

                inputs.dir(sourceSets.main.output.classesDir)
                inputs.dir(sourceSets.main.output.resourcesDir)
                inputs.dir(sourceSets.webserver.output.resourcesDir)
                inputs.dir(dependencyLibDir)
                inputs.dir(dependencyFrameworksDir)
                outputs.dir(applicationOutputDir)

                doLast {
                    if (project.treasureboat.applicationClass.length() == 0) {
                        throw new InvalidUserDataException('TBApplication builds need to define an applicationClass property on the project level!')
                    }

                    sourceSets.main.output.classesDir.mkdirs()
                    sourceSets.main.output.resourcesDir.mkdirs()
                    sourceSets.webserver.output.resourcesDir.mkdirs()
                    dependencyLibDir.mkdirs()
                    dependencyFrameworksDir.mkdirs()

                    ant.taskdef(name: 'woapplication', classname: 'org.objectstyle.woproject.ant.WOApplication', classpath: configurations.woproject.asPath)
                    ant.woapplication(name: project.name,
                            destDir: project.buildDir, javaVersion: project.targetCompatibility,
                            principalClass: project.treasureboat.applicationClass,
                            cfBundleVersion: project.version, cfBundleShortVersion: project.version,
                            frameworksBaseURL: "/WebObjects/${project.name}.woa/Contents/Frameworks") {
                        classes(dir: sourceSets.main.output.classesDir)
                        resources(dir: sourceSets.main.output.resourcesDir)
                        wsresources(dir: sourceSets.webserver.output.resourcesDir)
                        lib(dir: dependencyLibDir)

                        frameworks(dir: dependencyFrameworksDir, embed: 'true') {
                            include(name: '**/*.framework')
                        }
                    }


                    // Store classpath of woproject in file
                    // def classpathFile = new File(new File(applicationOutputDir as String), 'classpath.txt')
                    // classpathFile.write(configurations.woproject.asPath)

                    // Add TBHashCode to ant created info.plist in the root of the Application.woa (replace)
                    // TBExecutable in the Info.plist should contain the project name including version otherwise WebServerResources
                    // are not loaded properly
                    def infoPlistFile = new File(new File(applicationOutputDir, 'Contents'), 'Info.plist')
                    ant.replace(file: infoPlistFile, token: "replaceHashCode", value: TBHashCode)
                    ant.replace(file: infoPlistFile, token: project.name, value: TBExecutable)

                    // Add Property to application launch script
                    def appLaunchScript = new File(applicationOutputDir, project.name)
                    def appClass = project.treasureboat.applicationClass
                    def echoStr = 'echo ${JAVA_EXECUTABLE} ${JAVA_EXECUTABLE_ARGS} -classpath \\"${THE_CLASSPATH}\\" ${APPLICATION_CLASS} ${COMMAND_LINE_ARGS}'
                    def evalStr = 'eval exec ${JAVA_EXECUTABLE} ${JAVA_EXECUTABLE_ARGS} -classpath "${THE_CLASSPATH}" ${APPLICATION_CLASS} ${COMMAND_LINE_ARGS}'
                    // See http://groovy-lang.org/syntax.html#_escaping_special_characters (extra $ is needed)
                    def propertyStr = " -WOFrameworksBaseURL /WebObjects/${woaVersionedName}/Frameworks -DWOApplicationClass=${appClass} -Dorg.treasureboat.pid=\$\$\$"

                    ant.replace(file: appLaunchScript, token: echoStr, value: echoStr + propertyStr)
                    ant.replace(file: appLaunchScript, token: evalStr, value: evalStr + propertyStr)
                    ant.chmod(file: appLaunchScript, perm: 755) // restore permissions after file is changed
                }
            }

            task('TBApplicationTarGz', type: Tar, dependsOn: [woapplication]) {
                description 'Build a .tar.gz file containing the complete application'

                extension = 'tar.gz'
                baseName = project.name
                appendix = 'Application'
                compression = Compression.GZIP
                into(woaVersionedName)
                from(applicationOutputDir)

            }

            task('TBWebServerTarGz', type: Tar, dependsOn: [woapplication]) {
                description 'Build a .tar.gz file for the WebServer'
                extension = 'tar.gz'
                baseName = project.name
                appendix = 'WebServerResources'
                compression = Compression.GZIP
                version = project.version
                def appContents = new File(applicationOutputDir, 'Contents')
                from(appContents) {
                    exclude '**/Resources/**'
                    exclude '**/MacOS/**'
                    exclude '**/UNIX/**'
                    exclude '**/Windows/**'
                    exclude '**/Info.plist'
                    exclude 'WebServerResources' // only exclude the directory
                    exclude project.name
                    exclude(project.name + '.cmd')
                    into(woaVersionedName)
                    // directory name inside zip package (does not include data/time since it is deployed on same folder on webserver)
                }

                def webServerContents = new File(new File(applicationOutputDir, 'Contents'), 'WebServerResources')
                into(woaVersionedName + '/Contents/WebServerResources') {
                        from(webServerContents)
                    }
                }
        }

    }

    def configureEclipseProject(Project project) {
        project.eclipse {
            project.eclipse.project {
                natures 'org.objectstyle.wolips.incrementalapplicationnature'
                buildCommand 'org.objectstyle.wolips.incrementalbuilder'
            }
        }
    }
}