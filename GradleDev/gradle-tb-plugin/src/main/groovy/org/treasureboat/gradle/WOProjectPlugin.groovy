// Copyright (c) 2015, Xyrality
// All rights reserved.
// Modified for TreasureBoat by Plotters, August 2016

package org.treasureboat.gradle

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.CopyMoveHelper.CopyOptions
import java.nio.file.StandardCopyOption
import java.util.jar.*
import java.util.zip.*

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import groovy.util.Node

import org.gradle.api.specs.Specs
import org.gradle.api.tasks.*
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.bundling.*
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.testing.jacoco.plugins.JacocoPlugin

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Treasureboat Gradle plugin with the following functionality:
 * <p>
 * <p> -    build Frameworks and Applications
 * <p> -    deploy to servers
 * <p> -    publish artifacts to Maven local or remote artifactory repository
 * <p> -    documentation: JavaDocs & Dash docsets
 * @author Dennis Bliefernicht (Xyrality)
 * @author plotters
 */
class WOProject implements Plugin<Project> {
    void apply(Project project) {
        project.getPlugins().apply(BasePlugin.class)
        project.getPlugins().apply(JavaPlugin.class)
        project.getPlugins().apply(MavenPlugin.class)
        project.getPlugins().apply(EclipsePlugin.class)

        project.extensions.create('treasureboat', WOProjectPluginExtension)

        if (!project.hasProperty('version') || (project.version == 'unspecified')) {
            throw new InvalidUserDataException('Subprojects need to define their versions')
        }

        project.ext {
            versionNameSuffix = (new Date()).format('-yyyyMMdd-HHmm')
            versionHashSuffix = project.TBHashCode
            dependencyLibDir = new File(project.buildDir, 'dependency-libs')
            dependencyFrameworksDir = new File(project.buildDir, 'dependency-frameworks')
            LibraryDir = new File(project.projectDir, 'Libraries')
            def env = System.getenv()
            def userHome = env['HOME']
            frameworksLocalDir = new File(userHome + '/Library/Frameworks/')
        }

        project.sourceCompatibility = '1.8'
        project.targetCompatibility = '1.8'

        configureFluffyBunnyProjectStructure(project)
        configureEclipseClasspath(project)
        configureWOProjectDependency(project)
        configureDependenciesTask(project)
        configureResourceTasks(project)
        configureJarTasks(project)
        installRepositories(project)
        addTestDependencies(project)
    }


    def configureWOProjectDependency(Project project) {
        project.with {

            def woProjectLibs = new File(project.rootProject.projectDir, 'gradle-install-libraries/libs')
            def woProjectLocalPath = new File(woProjectLibs, 'woproject.jar')
            configurations { woproject }

            if (project.hasProperty('nexusUrl')) {
                repositories {
                    maven {
                        credentials {
                            username "${nexusUsername}"
                            password "${nexusPassword}"
                        }
                        if (tbVersion.endsWith('-SNAPSHOT')) {
                            url "${nexusUrl}/repository/maven-snapshots/"
                        } else {
                            url "${nexusUrl}/repository/maven-public/"
                        }
                    }
                }
            }

            dependencies {
                woproject "org.treasureboat.libraries:woproject:$tbVersion"
                //                 woproject "org.treasureboat.libraries:core-project:1.0.0"

                //woproject files(woProjectLocalPath)
            }
        }
    }

    def configureFluffyBunnyProjectStructure(Project project) {
        project.with {
            sourceSets {
                main {
                    java { srcDirs = ['Sources', 'GeneratedEOs'] }
                    resources { srcDirs = ['Resources'] }
                }

                test {
                    java { srcDirs = ['TestSources'] }
                    resources { srcDirs = ['TestResources'] }
                    runtimeClasspath = sourceSets.main.output + files(output.resourcesDir) + files(output.classesDir) + configurations.testRuntime
                }

                integrationTest {
                    java { srcDirs = ['TestTransferSources'] }
                    runtimeClasspath = sourceSets.main.output + files(output.resourcesDir) + files(output.classesDir) + configurations.testRuntime
                }

                webserver {
                    resources { srcDirs = ['WebServerResources'] }
                }

                components {
                    resources { srcDirs = ['Components'] }
                }
            }
        }
    }

    // *** Task: copydependencies ***
    /**
     * Task Copy Project dependencies
     *
     * @param project Project
     */
    def configureDependenciesTask(Project project) {
        project.with {
            task('copyDependencies', dependsOn: classes) {
                description 'copies all JARs on which this build depends on to the dependency-libs directory, converting frameworks in the process'

                outputs.dir(dependencyLibDir)
                outputs.dir(dependencyFrameworksDir)

                doLast {
                    dependencyLibDir.mkdirs()
                    dependencyFrameworksDir.mkdirs()

                    configurations.runtime.resolvedConfiguration.getFiles(Specs.satisfyAll()).each {
                        def basename = it.toPath().getFileName().toString()
                        def jarFile = new JarFile(it)
                        logger.info("basename: " + basename)
                        def jarEntry = jarFile.getJarEntry("Resources/Info.plist")
                        if (jarEntry != null) {
                            def xmlContent = new Scanner(jarFile.getInputStream(jarEntry)).useDelimiter("\\A").next()
                            logger.info("XmlContent = " + xmlContent)
                            def parser = new XmlParser(false, false, true)
                            parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                            def plist = parser.parseText(xmlContent)

                            def curKey = ""
                            def valueMap = [:]
                            plist.dict[0].children().each {
                                if (it instanceof Node) {
                                    switch (it.name()) {
                                        case "key":
                                            curKey = it.value()[0]
                                            break
                                        case "string":
                                            valueMap.put(curKey, it.value()[0])
                                            break
                                        case "true":
                                            valueMap.put(curKey, true)
                                            break
                                        case "false":
                                            valueMap.put(curKey, false)
                                            break
                                        case "array":
                                            valueMap.put(curKey, it.children().collect { x -> x.value()[0] })
                                            break
                                    }
                                }
                            }
                            logger.info("Valuemap = " + valueMap.toString())
                            def bundleName = valueMap.get("TBBundleExecutable")

                            // Do not treat TBParser as a framework, otherwise this will happen:
//                            * What went wrong:
//                            Execution failed for task ':Frameworks/Private/TBEnterprise:copyDependencies'.
//                                    > invalid entry compressed size (expected 1832 but got 1827 bytes)
                            if (bundleName == "TBParser") {
                                def outputFile = new File(dependencyLibDir, basename)
                                logger.info(basename + " is no framework, will copy to libs folder")
                                Files.copy(it.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                return
                            }


                            if (valueMap.get("TBBundlePackageType") == "FMWK") {
                                def outputDir = new File(dependencyFrameworksDir, bundleName + ".framework")
                                def jarsDir = new File(new File(outputDir, "Resources"), "Java")
                                def jarOutputFile = new File(jarsDir, bundleName.toLowerCase() + ".jar")
                                logger.info('*********************** Extra info *************************')
                                logger.info("jarsDir        : " + jarsDir.toString())
                                logger.info("jarOutputFile  : " + jarOutputFile.toString())
                                logger.info("outputDir      : " + outputDir.toString())

                                logger.info(bundleName + " is a framework, will convert to .framework folder " + basename)

                                jarsDir.mkdirs()
                                outputDir.mkdirs()

                                def jarOutput = new ZipOutputStream(new FileOutputStream(jarOutputFile))
                                for (JarEntry entry : jarFile.entries()) {
                                    def entryFileName = entry.getName()
                                    logger.info(entryFileName)
                                    if ((entryFileName.startsWith("Resources") && !entryFileName.contains("/WebServerResources/")) || entryFileName.startsWith("WebServerResources")) {
                                        File outputFile = new File(outputDir, entryFileName)
                                        if (!entry.isDirectory()) {
                                            outputFile.parentFile.mkdirs()
                                            InputStream inputStream = jarFile.getInputStream(entry)
                                            FileOutputStream outputStream = new FileOutputStream(outputFile)
                                            IOUtils.copy(inputStream, outputStream)
                                            IOUtils.closeQuietly(inputStream)
                                            IOUtils.closeQuietly(outputStream)
                                        }
                                    } else if (!entryFileName.startsWith("META-INF")) {
                                        InputStream inputStream = jarFile.getInputStream(entry)
                                        jarOutput.putNextEntry(new ZipEntry(entry))
                                        IOUtils.copy(inputStream, jarOutput)
                                    }
                                }
                                jarOutput.close()

                                return
                            }
                        }
                        // This code will not always run. When the dependency is not a framework only
//                        println('basename' + basename)
                        def outputFile = new File(dependencyLibDir, basename)
                        logger.info(basename + " is no framework, will copy to libs folder")
                        Files.copy(it.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
    }

    def configureResourceTasks(Project project) {
        project.with {
            task('copyComponents', type: Copy) {
                description 'copy TBComponents to resources and flatten'

                sourceSets.components.resources.srcDirs.each { from fileTree(it) }
                into(sourceSets.main.output.resourcesDir)
                includeEmptyDirs = false

                eachFile { details ->
                    details.path = details.path.replaceAll(/^.*\/([^\/]*\.wo|[^\/]*\.wo\/[^\n]*|[^\/]*.api)$/, {
                        "${it[1]}"
                    })
                }

                processResources.dependsOn it
            }

            task('copyWebServerResources', type: Copy) {
                description 'copy web server resources to WebServerResources'

                from sourceSets.webserver.resources
                into sourceSets.webserver.output.resourcesDir

                processResources.dependsOn it
            }
        }
    }

    def configureJarTasks(Project project) {
        project.with {
            jar {
                from(sourceSets.webserver.resources.srcDirs) { into('WebServerResources') }
            }

            task('testJar', type: Jar, dependsOn: testClasses) {
                classifier = 'tests'
                from sourceSets.test.output

                assemble.dependsOn it
            }

            task('sourcesJar', type: Jar, dependsOn: classes) {
                classifier = 'sources'
                from sourceSets.main.allSource
            }

            configurations { tests }

            artifacts {
                tests testJar
                // archives sourcesJar
            }
        }
    }

    def addTestDependencies(Project project) {

        project.dependencies {
            testCompile 'org.mockito:mockito-all:1.10.19'
//          testCompile group: 'com.wounit', name: 'wounit', version: '1.2.1'
            testCompile group: 'junit', name: 'junit', version: '4.13.1'
            testCompile("org.junit.jupiter:junit-jupiter:5.6.2")
            testRuntime("org.junit.jupiter:junit-jupiter:5.6.2")
        }
    }

    def installRepositories(Project project) {
        project.repositories {
            mavenLocal()
            mavenCentral()
        }
    }

    def configureEclipseClasspath(Project project) {
        project.with {
            eclipse {
                classpath {
                    downloadJavadoc = true
                    file {
                        whenMerged { classpath ->
                            def erExtensions = classpath.entries.findAll { entry -> entry.path.contains('ERExtensions') }

                            classpath.entries.removeAll(erExtensions)
                            for (classpathEntry in erExtensions) {
                                classpath.entries.add(0, classpathEntry)
                            }
                        }
                    }
                }
            }

            eclipseClasspath.doLast {
                eclipse.classpath.sourceSets.main.allSource.each { it.mkdirs() }
                eclipse.classpath.sourceSets.main.output.each { it.mkdirs() }
                eclipse.classpath.sourceSets.test.output.each { it.mkdirs() }
                eclipse.classpath.sourceSets.integrationTest.output.each { it.mkdirs() }
            }
        }
    }
}

class WOProjectPluginExtension {
    def deploymentServers = []
    def deploymentPath = ''
    def webserverResourcePath = '/opt/Local/Library/WebObjects/WebServerResource/'
    def deploymentSSHPort = 22
    def deploymentSSHUser = ''
    def deploymentSSHIgnoreHostKey = false
    def deploymentMonitorBounceTasks = [:]
//    def deploymentMonitorURLPattern = 'http://%s:1085/WebObjects/JavaMonitor.woa/'
    def deploymentWebserver = ''
    def deploymentWebserverSSHUser = ''
    def deploymentMonitorURLPattern = 'http://%s/WebObjects/JavaMonitor.woa/'
    def applicationClass = ''
    def principalClass = ''
}
