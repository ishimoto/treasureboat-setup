// Copyright (c) 2015, Xyrality
// All rights reserved.
// Modified for TreasureBoat by Plotters, August 2016

package org.treasureboat.gradle

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.Project

class TBFramework extends WOProject {

    File frameworkOutputDir

    void apply(Project project) {
        super.apply(project)

        frameworkOutputDir = new File(project.buildDir, project.name + '.framework')

        configureEclipseProject(project)
        configureWOFrameworkTask(project)
        configureJarTaskResources(project)

        project.afterEvaluate {
            installToLocalLibrary(project)
            cleanLocalLibrary(project)
        }

    }

    def configureJarTaskResources(Project project) {
        project.with {
            jar {
                def infoPlistFile = new File(new File(frameworkOutputDir, 'Resources'), 'Info.plist')

                it.dependsOn project.woframework
                inputs.file(infoPlistFile)
//                println 'Included jars in frameworks:' + infoPlistFile
                into('Resources') {
                    from infoPlistFile
                    from sourceSets.main.output.resourcesDir
                }
            }
        }
    }


    def configureWOFrameworkTask(Project project) {
        project.with {
            project.task('woframework', dependsOn: [project.classes, project.copyDependencies]) {
                description 'Build this framework as TreasureBoat framework structure'
//                println 'classesDir  = ' + sourceSets.main.output.classesDir
//                println 'resoucesDir = ' + sourceSets.main.output.resourcesDir
                inputs.dir(sourceSets.main.output.classesDir)
                inputs.dir(sourceSets.main.output.resourcesDir)
                inputs.dir(sourceSets.webserver.output.resourcesDir)
                inputs.dir(dependencyLibDir)
                outputs.dir(frameworkOutputDir)

                doLast {
                    sourceSets.main.output.classesDir.mkdirs()
                    sourceSets.main.output.resourcesDir.mkdirs()
                    sourceSets.webserver.output.resourcesDir.mkdirs()
                    dependencyLibDir.mkdirs()
                    logger.info("Configuration: " + configurations.woproject.asPath)
                    ant.taskdef(name:'woframework', classname:'org.objectstyle.woproject.ant.WOFramework', classpath: configurations.woproject.asPath)
                    ant.woframework(name:project.name,
                            destDir:project.buildDir, javaVersion:project.targetCompatibility,
                            cfBundleShortVersion: project.version,
                            cfBundleVersion: project.version, principalClass: project.treasureboat.principalClass) {
                        classes(dir:sourceSets.main.output.classesDir)
                        resources(dir:sourceSets.main.output.resourcesDir)
                        wsresources(dir:sourceSets.webserver.output.resourcesDir)
                        lib(dir:dependencyLibDir)
//                        lib(dir:LibraryDir) // Use dependency jars from Libraries folder
                        // When 'dependencyLibDir' is used (the default), all jars needed to compile the framework will be included in the framework as well.
                        // This is not the case when the Framework is published to a (local) Maven repository and Maven dependencies are used
                        // *** this will work if we switch to Maven dependencies only and don't build frameworks anymore in ~/Library/Frameworks ***
                    }
                    // Add TBHashCode to ant created info.plist (replace)
                    def infoPlistFile = new File(new File(frameworkOutputDir, 'Resources'), 'Info.plist')
                    ant.replace(file: infoPlistFile, token: "replaceHashCode", value: TBHashCode)
                }
            }
        }
    }

    def configureEclipseProject(Project project) {
        project.with {
            eclipse {
                eclipse.project {
                    natures 'org.objectstyle.wolips.incrementalframeworknature'
                    buildCommand 'org.objectstyle.wolips.incrementalbuilder'
                }
            }
        }
    }

    def installToLocalLibrary (Project project) {
        project.with {
            def env = System.getenv()
            def userHome = env['HOME']
            def frameworksLocalDir = new File(userHome+'/Library/Frameworks/' + project.name + '.framework')

            task('createDir') {
                description 'Create directory for local framework'
                frameworksLocalDir.mkdir()
            }
            task('installFrameworks', type: Copy, dependsOn: 'createDir') {
                description 'Installs Framework into Local Library folder'

                into(frameworksLocalDir)
                from fileTree(frameworkOutputDir)
            }
        }
    }

    def cleanLocalLibrary (Project project) {
        project.with {
            def env = System.getenv()
            def userHome = env['HOME']
            def frameworksLocalDir = new File(userHome+'/Library/Frameworks/' + project.name + '.framework')

            task('cleanFrameworks') {
                description 'Clean directory for local framework'
                frameworksLocalDir.deleteDir()
            }
        }
    }

}